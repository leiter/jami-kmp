//
//  JamiBridgeWrapper.mm
//  GetTogether
//
//  Real libjami integration - replaces mock implementation
//  Bridges Objective-C to libjami C++ API
//

#import "JamiBridgeWrapper.h"
#import <UIKit/UIKit.h>

// C++ Standard Library
#include <string>
#include <vector>
#include <map>
#include <functional>
#include <filesystem>

// =============================================================================
// Inline File Logger - writes to Documents folder for crash-safe debugging
// =============================================================================

static NSFileHandle *g_logFileHandle = nil;
static NSDateFormatter *g_logDateFormatter = nil;
static dispatch_queue_t g_logQueue = nil;

static void initFileLogger() {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
        NSString *documentsPath = paths.firstObject;
        if (!documentsPath) {
            NSLog(@"[FileLogger] ERROR: Could not find Documents directory");
            return;
        }

        NSDateFormatter *timestampFormatter = [[NSDateFormatter alloc] init];
        timestampFormatter.dateFormat = @"yyyy-MM-dd_HH-mm-ss";
        NSString *timestamp = [timestampFormatter stringFromDate:[NSDate date]];

        NSString *fileName = [NSString stringWithFormat:@"gettogether_native_%@.log", timestamp];
        NSString *logFilePath = [documentsPath stringByAppendingPathComponent:fileName];

        [[NSFileManager defaultManager] createFileAtPath:logFilePath contents:nil attributes:nil];
        g_logFileHandle = [NSFileHandle fileHandleForWritingAtPath:logFilePath];

        g_logDateFormatter = [[NSDateFormatter alloc] init];
        g_logDateFormatter.dateFormat = @"HH:mm:ss.SSS";

        g_logQueue = dispatch_queue_create("com.gettogether.filelogger", DISPATCH_QUEUE_SERIAL);

        // Write header
        NSString *header = [NSString stringWithFormat:
            @"=====================================\n"
            @"GetTogether iOS Native Debug Log\n"
            @"Started: %@\n"
            @"=====================================\n\n",
            [NSDate date]];
        NSData *data = [header dataUsingEncoding:NSUTF8StringEncoding];
        [g_logFileHandle writeData:data];
        [g_logFileHandle synchronizeFile];

        NSLog(@"[FileLogger] Initialized at %@", logFilePath);
    });
}

static void fileLog(const char* level, const char* tag, NSString *message) {
    initFileLogger();

    // Also log to NSLog
    NSLog(@"%s/%s: %@", level, tag, message);

    if (!g_logFileHandle) return;

    dispatch_async(g_logQueue, ^{
        NSString *timestamp = [g_logDateFormatter stringFromDate:[NSDate date]];
        NSString *line = [NSString stringWithFormat:@"[%@] %s/%s: %@\n", timestamp, level, tag, message];
        NSData *data = [line dataUsingEncoding:NSUTF8StringEncoding];
        [g_logFileHandle writeData:data];
        [g_logFileHandle synchronizeFile];
    });
}

// Convenience macros for file logging
#define FILE_LOG_I(tag, ...) do { \
    NSString *msg = [NSString stringWithFormat:__VA_ARGS__]; \
    fileLog("I", tag, msg); \
} while(0)

#define FILE_LOG_D(tag, ...) do { \
    NSString *msg = [NSString stringWithFormat:__VA_ARGS__]; \
    fileLog("D", tag, msg); \
} while(0)

#define FILE_LOG_W(tag, ...) do { \
    NSString *msg = [NSString stringWithFormat:__VA_ARGS__]; \
    fileLog("W", tag, msg); \
} while(0)

#define FILE_LOG_E(tag, ...) do { \
    NSString *msg = [NSString stringWithFormat:__VA_ARGS__]; \
    fileLog("E", tag, msg); \
} while(0)

// libjami C++ headers
#include "jami.h"
#include "configurationmanager_interface.h"
#include "callmanager_interface.h"
#include "conversation_interface.h"
#include "presencemanager_interface.h"
#include "datatransfer_interface.h"
#include "account_const.h"

using namespace libjami;

// =============================================================================
// Type Conversion Helpers
// =============================================================================

// C++ string -> NSString
static inline NSString* toNSString(const std::string& str) {
    return [NSString stringWithUTF8String:str.c_str()];
}

// NSString -> C++ string
static inline std::string toCppString(NSString* str) {
    return str ? std::string([str UTF8String]) : "";
}

// std::map<string,string> -> NSDictionary
static NSDictionary<NSString*, NSString*>* toNSDictionary(const std::map<std::string, std::string>& map) {
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:map.size()];
    for (const auto& pair : map) {
        dict[toNSString(pair.first)] = toNSString(pair.second);
    }
    return [dict copy];
}

// NSDictionary -> std::map<string,string>
static std::map<std::string, std::string> toCppMap(NSDictionary<NSString*, NSString*>* dict) {
    std::map<std::string, std::string> map;
    for (NSString* key in dict) {
        map[toCppString(key)] = toCppString(dict[key]);
    }
    return map;
}

// std::vector<string> -> NSArray
static NSArray<NSString*>* toNSArray(const std::vector<std::string>& vec) {
    NSMutableArray *arr = [NSMutableArray arrayWithCapacity:vec.size()];
    for (const auto& str : vec) {
        [arr addObject:toNSString(str)];
    }
    return [arr copy];
}

// NSArray -> std::vector<string>
static std::vector<std::string> toCppVector(NSArray<NSString*>* arr) {
    std::vector<std::string> vec;
    vec.reserve(arr.count);
    for (NSString* str in arr) {
        vec.push_back(toCppString(str));
    }
    return vec;
}

// std::map<string,int32_t> -> NSDictionary<NSString*, NSNumber*>
static NSDictionary<NSString*, NSNumber*>* toNSNumberDictionary(const std::map<std::string, int32_t>& map) {
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:map.size()];
    for (const auto& pair : map) {
        dict[toNSString(pair.first)] = @(pair.second);
    }
    return [dict copy];
}

// Convert libjami::SwarmMessage to JBSwarmMessage
static JBSwarmMessage* toJBSwarmMessage(const SwarmMessage& msg) {
    JBSwarmMessage *jbMsg = [[JBSwarmMessage alloc] init];
    jbMsg.messageId = toNSString(msg.id);
    jbMsg.type = toNSString(msg.type);
    jbMsg.author = toNSString(msg.body.count("author") ? msg.body.at("author") : "");
    jbMsg.body = toNSDictionary(msg.body);
    jbMsg.status = toNSNumberDictionary(msg.status);
    jbMsg.timestamp = 0; // Will be set from body if available
    jbMsg.replyTo = nil;

    // Extract timestamp from body if present
    if (msg.body.count("timestamp")) {
        jbMsg.timestamp = std::stoll(msg.body.at("timestamp"));
    }

    // Extract replyTo from body if present
    if (msg.body.count("reply-to")) {
        jbMsg.replyTo = toNSString(msg.body.at("reply-to"));
    }

    // Convert reactions
    NSMutableArray *reactions = [NSMutableArray arrayWithCapacity:msg.reactions.size()];
    for (const auto& reaction : msg.reactions) {
        [reactions addObject:toNSDictionary(reaction)];
    }
    jbMsg.reactions = [reactions copy];

    return jbMsg;
}

// Convert registration state string to enum
static JBRegistrationState toRegistrationState(const std::string& state) {
    if (state == Account::States::REGISTERED || state == Account::States::READY) {
        return JBRegistrationStateRegistered;
    } else if (state == Account::States::TRYING) {
        return JBRegistrationStateTrying;
    } else if (state == Account::States::UNREGISTERED) {
        return JBRegistrationStateUnregistered;
    } else if (state == Account::States::ERROR_AUTH) {
        return JBRegistrationStateErrorAuth;
    } else if (state == Account::States::ERROR_NETWORK) {
        return JBRegistrationStateErrorNetwork;
    } else if (state == Account::States::ERROR_HOST) {
        return JBRegistrationStateErrorHost;
    } else if (state == Account::States::ERROR_SERVICE_UNAVAILABLE) {
        return JBRegistrationStateErrorServiceUnavailable;
    } else if (state == Account::States::ERROR_NEED_MIGRATION) {
        return JBRegistrationStateErrorNeedMigration;
    } else if (state == Account::States::INITIALIZING) {
        return JBRegistrationStateInitializing;
    } else if (state.find("ERROR") != std::string::npos) {
        return JBRegistrationStateErrorGeneric;
    }
    return JBRegistrationStateUnregistered;
}

// Convert call state string to enum
static JBCallState toCallState(const std::string& state) {
    if (state == "INCOMING") {
        return JBCallStateIncoming;
    } else if (state == "CONNECTING") {
        return JBCallStateConnecting;
    } else if (state == "RINGING") {
        return JBCallStateRinging;
    } else if (state == "CURRENT") {
        return JBCallStateCurrent;
    } else if (state == "HUNGUP") {
        return JBCallStateHungup;
    } else if (state == "BUSY") {
        return JBCallStateBusy;
    } else if (state == "FAILURE") {
        return JBCallStateFailure;
    } else if (state == "HOLD") {
        return JBCallStateHold;
    } else if (state == "UNHOLD") {
        return JBCallStateUnhold;
    } else if (state == "OVER") {
        return JBCallStateOver;
    }
    return JBCallStateInactive;
}

// =============================================================================
// Data Class Implementations
// =============================================================================

@implementation JBContact
@end

@implementation JBTrustRequest
@end

@implementation JBConversationMember
@end

@implementation JBConversationRequest
@end

@implementation JBLookupResult
@end

@implementation JBFileTransferInfo
@end

@implementation JBSwarmMessage
@end

// =============================================================================
// JamiBridgeWrapper Implementation
// =============================================================================

@interface JamiBridgeWrapper ()

@property (nonatomic, assign) BOOL daemonRunning;
@property (nonatomic, copy) NSString *dataPath;

@end

@implementation JamiBridgeWrapper

// =============================================================================
// Singleton
// =============================================================================

+ (instancetype)shared {
    static JamiBridgeWrapper *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[JamiBridgeWrapper alloc] init];
    });
    return instance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _daemonRunning = NO;
    }
    return self;
}

// =============================================================================
// Signal Handler Registration
// =============================================================================

- (void)registerSignalHandlers {
    std::map<std::string, std::shared_ptr<CallbackWrapperBase>> handlers;

    __weak JamiBridgeWrapper *weakSelf = self;

    // =========================================================================
    // Configuration/Account Signals
    // =========================================================================

    // Registration state changed
    handlers.insert(exportable_callback<ConfigurationSignal::RegistrationStateChanged>(
        [weakSelf](const std::string& accountId, const std::string& state,
                   int code, const std::string& detail) {
            FILE_LOG_I("JamiBridge-C++", @"RegistrationStateChanged CALLBACK: account=%s state=%s code=%d detail=%s",
                  accountId.c_str(), state.c_str(), code, detail.c_str());
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            JBRegistrationState stateEnum = toRegistrationState(state);
            NSString *detailNS = toNSString(detail);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf) {
                    FILE_LOG_I("JamiBridge-C++", @"RegistrationStateChanged dispatching: hasDelegate=%d",
                          strongSelf.delegate != nil);
                    if ([strongSelf.delegate respondsToSelector:@selector(onRegistrationStateChanged:state:code:detail:)]) {
                        [strongSelf.delegate onRegistrationStateChanged:accountIdNS
                                                                  state:stateEnum
                                                                   code:code
                                                                 detail:detailNS];
                        FILE_LOG_I("JamiBridge-C++", @"RegistrationStateChanged forwarded to delegate");
                    } else {
                        FILE_LOG_W("JamiBridge-C++", @"Delegate does not respond to onRegistrationStateChanged");
                    }
                } else {
                    FILE_LOG_E("JamiBridge-C++", @"RegistrationStateChanged: strongSelf is nil!");
                }
            });
        }));

    // Account details changed
    handlers.insert(exportable_callback<ConfigurationSignal::AccountDetailsChanged>(
        [weakSelf](const std::string& accountId, const std::map<std::string, std::string>& details) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSDictionary *detailsNS = toNSDictionary(details);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onAccountDetailsChanged:details:)]) {
                    [strongSelf.delegate onAccountDetailsChanged:accountIdNS
                                                         details:detailsNS];
                }
            });
        }));

    // Contact added
    handlers.insert(exportable_callback<ConfigurationSignal::ContactAdded>(
        [weakSelf](const std::string& accountId, const std::string& uri, bool confirmed) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *uriNS = toNSString(uri);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onContactAdded:uri:confirmed:)]) {
                    [strongSelf.delegate onContactAdded:accountIdNS
                                                    uri:uriNS
                                              confirmed:confirmed];
                }
            });
        }));

    // Contact removed
    handlers.insert(exportable_callback<ConfigurationSignal::ContactRemoved>(
        [weakSelf](const std::string& accountId, const std::string& uri, bool banned) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *uriNS = toNSString(uri);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onContactRemoved:uri:banned:)]) {
                    [strongSelf.delegate onContactRemoved:accountIdNS
                                                      uri:uriNS
                                                   banned:banned];
                }
            });
        }));

    // Incoming trust request
    handlers.insert(exportable_callback<ConfigurationSignal::IncomingTrustRequest>(
        [weakSelf](const std::string& accountId, const std::string& from,
                   const std::string& conversationId, const std::vector<uint8_t>& payload,
                   time_t received) {
            FILE_LOG_I("JamiBridge-C++", @"IncomingTrustRequest CALLBACK: account=%s from=%s convId=%s payloadSize=%zu received=%ld",
                  accountId.c_str(), from.c_str(), conversationId.c_str(), payload.size(), (long)received);
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *fromNS = toNSString(from);
            NSString *conversationIdNS = toNSString(conversationId);
            NSData *payloadData = [NSData dataWithBytes:payload.data() length:payload.size()];
            int64_t receivedNS = (int64_t)received;
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf) {
                    FILE_LOG_I("JamiBridge-C++", @"IncomingTrustRequest dispatching: hasDelegate=%d", strongSelf.delegate != nil);
                    if ([strongSelf.delegate respondsToSelector:@selector(onIncomingTrustRequest:conversationId:from:payload:received:)]) {
                        [strongSelf.delegate onIncomingTrustRequest:accountIdNS
                                                     conversationId:conversationIdNS
                                                               from:fromNS
                                                            payload:payloadData
                                                           received:receivedNS];
                        FILE_LOG_I("JamiBridge-C++", @"IncomingTrustRequest forwarded to delegate");
                    }
                } else {
                    FILE_LOG_E("JamiBridge-C++", @"IncomingTrustRequest: strongSelf is nil!");
                }
            });
        }));

    // Name registration ended
    handlers.insert(exportable_callback<ConfigurationSignal::NameRegistrationEnded>(
        [weakSelf](const std::string& accountId, int state, const std::string& name) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *nameNS = toNSString(name);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onNameRegistrationEnded:state:name:)]) {
                    [strongSelf.delegate onNameRegistrationEnded:accountIdNS
                                                           state:state
                                                            name:nameNS];
                }
            });
        }));

    // Registered name found (lookup result)
    handlers.insert(exportable_callback<ConfigurationSignal::RegisteredNameFound>(
        [weakSelf](const std::string& accountId, const std::string& requestName,
                   int state, const std::string& address, const std::string& name) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *addressNS = toNSString(address);
            NSString *nameNS = toNSString(name);
            JBLookupState lookupState;
            switch (state) {
                case 0: lookupState = JBLookupStateSuccess; break;
                case 1: lookupState = JBLookupStateNotFound; break;
                case 2: lookupState = JBLookupStateInvalid; break;
                default: lookupState = JBLookupStateError; break;
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onRegisteredNameFound:state:address:name:)]) {
                    [strongSelf.delegate onRegisteredNameFound:accountIdNS
                                                         state:lookupState
                                                       address:addressNS
                                                          name:nameNS];
                }
            });
        }));

    // Known devices changed
    handlers.insert(exportable_callback<ConfigurationSignal::KnownDevicesChanged>(
        [weakSelf](const std::string& accountId, const std::map<std::string, std::string>& devices) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSDictionary *devicesNS = toNSDictionary(devices);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onKnownDevicesChanged:devices:)]) {
                    [strongSelf.delegate onKnownDevicesChanged:accountIdNS
                                                       devices:devicesNS];
                }
            });
        }));

    // Composing status changed
    handlers.insert(exportable_callback<ConfigurationSignal::ComposingStatusChanged>(
        [weakSelf](const std::string& accountId, const std::string& convId,
                   const std::string& from, int status) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *convIdNS = toNSString(convId);
            NSString *fromNS = toNSString(from);
            BOOL isComposing = (status != 0);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onComposingStatusChanged:conversationId:from:isComposing:)]) {
                    [strongSelf.delegate onComposingStatusChanged:accountIdNS
                                                   conversationId:convIdNS
                                                             from:fromNS
                                                      isComposing:isComposing];
                }
            });
        }));

    // Profile received
    handlers.insert(exportable_callback<ConfigurationSignal::ProfileReceived>(
        [weakSelf](const std::string& accountId, const std::string& from, const std::string& vcard) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *fromNS = toNSString(from);
            NSString *vcardNS = toNSString(vcard);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onProfileReceived:from:displayName:avatarPath:)]) {
                    // Parse vcard to extract display name and avatar
                    // For now, pass the vcard as display name
                    [strongSelf.delegate onProfileReceived:accountIdNS
                                                      from:fromNS
                                               displayName:vcardNS
                                                avatarPath:nil];
                }
            });
        }));

    // =========================================================================
    // Call Signals
    // =========================================================================

    // Call state change
    handlers.insert(exportable_callback<CallSignal::StateChange>(
        [weakSelf](const std::string& accountId, const std::string& callId,
                   const std::string& state, int code) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *callIdNS = toNSString(callId);
            JBCallState stateEnum = toCallState(state);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
                    [strongSelf.delegate onCallStateChanged:accountIdNS
                                                     callId:callIdNS
                                                      state:stateEnum
                                                       code:code];
                }
            });
        }));

    // Incoming call
    handlers.insert(exportable_callback<CallSignal::IncomingCall>(
        [weakSelf](const std::string& accountId, const std::string& callId,
                   const std::string& peerId, const std::vector<std::map<std::string, std::string>>& mediaList) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *callIdNS = toNSString(callId);
            NSString *peerIdNS = toNSString(peerId);
            // Check if video is in media list
            bool hasVideo = false;
            for (const auto& media : mediaList) {
                if (media.count("MEDIA_TYPE") && media.at("MEDIA_TYPE") == "MEDIA_TYPE_VIDEO") {
                    hasVideo = true;
                    break;
                }
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onIncomingCall:callId:peerId:peerDisplayName:hasVideo:)]) {
                    [strongSelf.delegate onIncomingCall:accountIdNS
                                                 callId:callIdNS
                                                 peerId:peerIdNS
                                        peerDisplayName:peerIdNS
                                               hasVideo:hasVideo];
                }
            });
        }));

    // Audio muted
    handlers.insert(exportable_callback<CallSignal::AudioMuted>(
        [weakSelf](const std::string& callId, bool muted) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *callIdNS = toNSString(callId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onAudioMuted:muted:)]) {
                    [strongSelf.delegate onAudioMuted:callIdNS muted:muted];
                }
            });
        }));

    // Video muted
    handlers.insert(exportable_callback<CallSignal::VideoMuted>(
        [weakSelf](const std::string& callId, bool muted) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *callIdNS = toNSString(callId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onVideoMuted:muted:)]) {
                    [strongSelf.delegate onVideoMuted:callIdNS muted:muted];
                }
            });
        }));

    // Conference created
    handlers.insert(exportable_callback<CallSignal::ConferenceCreated>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   const std::string& conferenceId) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSString *conferenceIdNS = toNSString(conferenceId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConferenceCreated:conversationId:conferenceId:)]) {
                    [strongSelf.delegate onConferenceCreated:accountIdNS
                                              conversationId:conversationIdNS
                                                conferenceId:conferenceIdNS];
                }
            });
        }));

    // Conference changed
    handlers.insert(exportable_callback<CallSignal::ConferenceChanged>(
        [weakSelf](const std::string& accountId, const std::string& conferenceId,
                   const std::string& state) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conferenceIdNS = toNSString(conferenceId);
            NSString *stateNS = toNSString(state);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConferenceChanged:conferenceId:state:)]) {
                    [strongSelf.delegate onConferenceChanged:accountIdNS
                                                conferenceId:conferenceIdNS
                                                       state:stateNS];
                }
            });
        }));

    // Conference removed
    handlers.insert(exportable_callback<CallSignal::ConferenceRemoved>(
        [weakSelf](const std::string& accountId, const std::string& conferenceId) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conferenceIdNS = toNSString(conferenceId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConferenceRemoved:conferenceId:)]) {
                    [strongSelf.delegate onConferenceRemoved:accountIdNS
                                                conferenceId:conferenceIdNS];
                }
            });
        }));

    // Conference info updated
    handlers.insert(exportable_callback<CallSignal::OnConferenceInfosUpdated>(
        [weakSelf](const std::string& conferenceId,
                   const std::vector<std::map<std::string, std::string>>& participantInfos) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *conferenceIdNS = toNSString(conferenceId);
            NSMutableArray *infos = [NSMutableArray arrayWithCapacity:participantInfos.size()];
            for (const auto& info : participantInfos) {
                [infos addObject:toNSDictionary(info)];
            }
            NSArray *infosCopy = [infos copy];
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConferenceInfoUpdated:participantInfos:)]) {
                    [strongSelf.delegate onConferenceInfoUpdated:conferenceIdNS
                                                participantInfos:infosCopy];
                }
            });
        }));

    // Media change requested
    handlers.insert(exportable_callback<CallSignal::MediaChangeRequested>(
        [weakSelf](const std::string& accountId, const std::string& callId,
                   const std::vector<std::map<std::string, std::string>>& mediaList) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *callIdNS = toNSString(callId);
            NSMutableArray *list = [NSMutableArray arrayWithCapacity:mediaList.size()];
            for (const auto& media : mediaList) {
                [list addObject:toNSDictionary(media)];
            }
            NSArray *listCopy = [list copy];
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onMediaChangeRequested:callId:mediaList:)]) {
                    [strongSelf.delegate onMediaChangeRequested:accountIdNS
                                                         callId:callIdNS
                                                      mediaList:listCopy];
                }
            });
        }));

    // =========================================================================
    // Conversation Signals
    // =========================================================================

    // Conversation ready
    handlers.insert(exportable_callback<ConversationSignal::ConversationReady>(
        [weakSelf](const std::string& accountId, const std::string& conversationId) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConversationReady:conversationId:)]) {
                    [strongSelf.delegate onConversationReady:accountIdNS
                                              conversationId:conversationIdNS];
                }
            });
        }));

    // Conversation removed
    handlers.insert(exportable_callback<ConversationSignal::ConversationRemoved>(
        [weakSelf](const std::string& accountId, const std::string& conversationId) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConversationRemoved:conversationId:)]) {
                    [strongSelf.delegate onConversationRemoved:accountIdNS
                                                conversationId:conversationIdNS];
                }
            });
        }));

    // Conversation request received
    handlers.insert(exportable_callback<ConversationSignal::ConversationRequestReceived>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   std::map<std::string, std::string> metadata) {
            FILE_LOG_I("JamiBridge-C++", @"ConversationRequestReceived CALLBACK: account=%s convId=%s metadataCount=%zu",
                  accountId.c_str(), conversationId.c_str(), metadata.size());
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSDictionary *metadataNS = toNSDictionary(metadata);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf) {
                    FILE_LOG_I("JamiBridge-C++", @"ConversationRequestReceived dispatching: hasDelegate=%d", strongSelf.delegate != nil);
                    if ([strongSelf.delegate respondsToSelector:@selector(onConversationRequestReceived:conversationId:metadata:)]) {
                        [strongSelf.delegate onConversationRequestReceived:accountIdNS
                                                            conversationId:conversationIdNS
                                                                  metadata:metadataNS];
                        FILE_LOG_I("JamiBridge-C++", @"ConversationRequestReceived forwarded to delegate");
                    }
                } else {
                    FILE_LOG_E("JamiBridge-C++", @"ConversationRequestReceived: strongSelf is nil!");
                }
            });
        }));

    // Swarm message received
    handlers.insert(exportable_callback<ConversationSignal::SwarmMessageReceived>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   const SwarmMessage& message) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            JBSwarmMessage *messageNS = toJBSwarmMessage(message);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onMessageReceived:conversationId:message:)]) {
                    [strongSelf.delegate onMessageReceived:accountIdNS
                                            conversationId:conversationIdNS
                                                   message:messageNS];
                }
            });
        }));

    // Swarm message updated
    handlers.insert(exportable_callback<ConversationSignal::SwarmMessageUpdated>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   const SwarmMessage& message) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            JBSwarmMessage *messageNS = toJBSwarmMessage(message);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onMessageUpdated:conversationId:message:)]) {
                    [strongSelf.delegate onMessageUpdated:accountIdNS
                                           conversationId:conversationIdNS
                                                  message:messageNS];
                }
            });
        }));

    // Swarm loaded (messages loaded)
    handlers.insert(exportable_callback<ConversationSignal::SwarmLoaded>(
        [weakSelf](uint32_t requestId, const std::string& accountId,
                   const std::string& conversationId, std::vector<SwarmMessage> messages) {
            // Copy data before async dispatch to avoid use-after-free
            // Note: messages is passed by value so it's already a copy, but we convert to NS types early
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSMutableArray *msgArray = [NSMutableArray arrayWithCapacity:messages.size()];
            for (const auto& msg : messages) {
                [msgArray addObject:toJBSwarmMessage(msg)];
            }
            NSArray *msgArrayCopy = [msgArray copy];
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onMessagesLoaded:accountId:conversationId:messages:)]) {
                    [strongSelf.delegate onMessagesLoaded:(int)requestId
                                                accountId:accountIdNS
                                           conversationId:conversationIdNS
                                                 messages:msgArrayCopy];
                }
            });
        }));

    // Conversation member event
    handlers.insert(exportable_callback<ConversationSignal::ConversationMemberEvent>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   const std::string& memberUri, int event) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSString *memberUriNS = toNSString(memberUri);
            JBMemberEventType eventType;
            switch (event) {
                case 0: eventType = JBMemberEventTypeJoin; break; // Add
                case 1: eventType = JBMemberEventTypeJoin; break; // Joins
                case 2: eventType = JBMemberEventTypeLeave; break; // Leave
                case 3: eventType = JBMemberEventTypeBan; break; // Banned
                default: eventType = JBMemberEventTypeJoin; break;
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConversationMemberEvent:conversationId:memberUri:event:)]) {
                    [strongSelf.delegate onConversationMemberEvent:accountIdNS
                                                    conversationId:conversationIdNS
                                                         memberUri:memberUriNS
                                                             event:eventType];
                }
            });
        }));

    // Conversation profile updated
    handlers.insert(exportable_callback<ConversationSignal::ConversationProfileUpdated>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   std::map<std::string, std::string> profile) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSDictionary *profileNS = toNSDictionary(profile);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onConversationProfileUpdated:conversationId:profile:)]) {
                    [strongSelf.delegate onConversationProfileUpdated:accountIdNS
                                                       conversationId:conversationIdNS
                                                              profile:profileNS];
                }
            });
        }));

    // Reaction added
    handlers.insert(exportable_callback<ConversationSignal::ReactionAdded>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   const std::string& messageId, std::map<std::string, std::string> reaction) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSString *messageIdNS = toNSString(messageId);
            NSDictionary *reactionNS = toNSDictionary(reaction);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onReactionAdded:conversationId:messageId:reaction:)]) {
                    [strongSelf.delegate onReactionAdded:accountIdNS
                                          conversationId:conversationIdNS
                                               messageId:messageIdNS
                                                reaction:reactionNS];
                }
            });
        }));

    // Reaction removed
    handlers.insert(exportable_callback<ConversationSignal::ReactionRemoved>(
        [weakSelf](const std::string& accountId, const std::string& conversationId,
                   const std::string& messageId, const std::string& reactionId) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *conversationIdNS = toNSString(conversationId);
            NSString *messageIdNS = toNSString(messageId);
            NSString *reactionIdNS = toNSString(reactionId);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onReactionRemoved:conversationId:messageId:reactionId:)]) {
                    [strongSelf.delegate onReactionRemoved:accountIdNS
                                            conversationId:conversationIdNS
                                                 messageId:messageIdNS
                                                reactionId:reactionIdNS];
                }
            });
        }));

    // =========================================================================
    // Presence Signals
    // =========================================================================

    // New buddy notification (presence)
    handlers.insert(exportable_callback<PresenceSignal::NewBuddyNotification>(
        [weakSelf](const std::string& accountId, const std::string& buddyUri,
                   int status, const std::string& lineStatus) {
            // Copy data before async dispatch to avoid use-after-free
            NSString *accountIdNS = toNSString(accountId);
            NSString *buddyUriNS = toNSString(buddyUri);
            BOOL isOnline = (status != 0);
            dispatch_async(dispatch_get_main_queue(), ^{
                JamiBridgeWrapper *strongSelf = weakSelf;
                if (strongSelf && [strongSelf.delegate respondsToSelector:@selector(onPresenceChanged:uri:isOnline:)]) {
                    [strongSelf.delegate onPresenceChanged:accountIdNS
                                                       uri:buddyUriNS
                                                  isOnline:isOnline];
                }
            });
        }));

    // =========================================================================
    // iOS-specific callbacks
    // =========================================================================

    // Get app data path - iOS needs to provide the data directory
    handlers.insert(exportable_callback<ConfigurationSignal::GetAppDataPath>(
        [weakSelf](const std::string& name, std::vector<std::string>* paths) {
            JamiBridgeWrapper *strongSelf = weakSelf;
            if (strongSelf && strongSelf.dataPath) {
                paths->push_back(toCppString(strongSelf.dataPath));
            }
        }));

    // Get device name
    handlers.insert(exportable_callback<ConfigurationSignal::GetDeviceName>(
        [](std::vector<std::string>* names) {
            NSString *deviceName = [[UIDevice currentDevice] name];
            names->push_back(toCppString(deviceName));
        }));

    // Get hardware audio format
    handlers.insert(exportable_callback<ConfigurationSignal::GetHardwareAudioFormat>(
        [](std::vector<int32_t>* params) {
            // iOS standard audio format: 48000 Hz, stereo
            params->push_back(48000); // Sample rate
            params->push_back(2);     // Channels
        }));

    libjami::registerSignalHandlers(handlers);
    FILE_LOG_I("JamiBridge", @"Signal handlers registered successfully");
}

// =============================================================================
// Daemon Lifecycle
// =============================================================================

- (void)initDaemonWithDataPath:(NSString *)dataPath {
    FILE_LOG_I("JamiBridge", @"initDaemon with path: %@", dataPath);
    self.dataPath = dataPath;

    // Create data directory if it doesn't exist
    NSFileManager *fm = [NSFileManager defaultManager];
    if (![fm fileExistsAtPath:dataPath]) {
        NSError *error;
        [fm createDirectoryAtPath:dataPath withIntermediateDirectories:YES attributes:nil error:&error];
        if (error) {
            FILE_LOG_E("JamiBridge", @"Failed to create data directory: %@", error);
        }
    }

    // Initialize with iOS flags
    int flags = LIBJAMI_FLAG_CONSOLE_LOG | LIBJAMI_FLAG_IOS_EXTENSION;
    FILE_LOG_I("JamiBridge", @"Calling libjami::init with flags=%d", flags);

    if (!libjami::init(static_cast<InitFlag>(flags))) {
        FILE_LOG_E("JamiBridge", @"libjami::init() FAILED!");
        return;
    }
    FILE_LOG_I("JamiBridge", @"libjami::init() succeeded");

    [self registerSignalHandlers];
    FILE_LOG_I("JamiBridge", @"Daemon initialized successfully");
}

- (void)startDaemon {
    FILE_LOG_I("JamiBridge", @"startDaemon called");

    std::filesystem::path configPath;  // Empty = use default
    FILE_LOG_I("JamiBridge", @"Calling libjami::start()...");
    if (!libjami::start(configPath)) {
        FILE_LOG_E("JamiBridge", @"libjami::start() FAILED!");
        return;
    }

    self.daemonRunning = YES;
    FILE_LOG_I("JamiBridge", @"Daemon started successfully, daemonRunning=YES");
}

- (void)stopDaemon {
    NSLog(@"[JamiBridge] stopDaemon");
    libjami::fini();
    self.daemonRunning = NO;
    NSLog(@"[JamiBridge] Daemon stopped");
}

- (BOOL)isDaemonRunning {
    return libjami::initialized() && self.daemonRunning;
}

// =============================================================================
// Account Management
// =============================================================================

- (NSString *)createAccountWithDisplayName:(NSString *)displayName password:(NSString *)password {
    FILE_LOG_I("JamiBridge", @"createAccount: displayName=%@", displayName);

    std::map<std::string, std::string> details;
    details[Account::ConfProperties::TYPE] = Account::ProtocolNames::RING;
    details[Account::ConfProperties::ALIAS] = toCppString(displayName);
    details[Account::ConfProperties::DISPLAYNAME] = toCppString(displayName);
    details[Account::ConfProperties::ARCHIVE_PASSWORD] = toCppString(password);
    details[Account::ConfProperties::PROXY_ENABLED] = "true";
    details[Account::ConfProperties::UPNP_ENABLED] = "false";
    details[Account::ConfProperties::TURN::ENABLED] = "true";
    details[Account::ConfProperties::TURN::SERVER] = "turn.jami.net";

    FILE_LOG_I("JamiBridge", @"Calling libjami::addAccount()...");
    std::string accountId = libjami::addAccount(details);
    FILE_LOG_I("JamiBridge", @"Account created: accountId=%s", accountId.c_str());

    // Log current account list
    auto accounts = libjami::getAccountList();
    FILE_LOG_I("JamiBridge", @"Current account count: %zu", accounts.size());

    return toNSString(accountId);
}

- (NSString *)importAccountFromArchive:(NSString *)archivePath password:(NSString *)password {
    NSLog(@"[JamiBridge] importAccount from: %@", archivePath);

    std::map<std::string, std::string> details;
    details[Account::ConfProperties::TYPE] = Account::ProtocolNames::RING;
    details[Account::ConfProperties::ARCHIVE_PATH] = toCppString(archivePath);
    details[Account::ConfProperties::ARCHIVE_PASSWORD] = toCppString(password);

    std::string accountId = libjami::addAccount(details);
    return toNSString(accountId);
}

- (BOOL)exportAccount:(NSString *)accountId
    toDestinationPath:(NSString *)destinationPath
         withPassword:(NSString *)password {
    NSLog(@"[JamiBridge] exportAccount: %@ to %@", accountId, destinationPath);
    return libjami::exportToFile(toCppString(accountId), toCppString(destinationPath), "", toCppString(password));
}

- (void)deleteAccount:(NSString *)accountId {
    NSLog(@"[JamiBridge] deleteAccount: %@", accountId);
    libjami::removeAccount(toCppString(accountId));
}

- (NSArray<NSString *> *)getAccountIds {
    std::vector<std::string> accounts = libjami::getAccountList();
    return toNSArray(accounts);
}

- (NSDictionary<NSString *, NSString *> *)getAccountDetails:(NSString *)accountId {
    auto details = libjami::getAccountDetails(toCppString(accountId));
    return toNSDictionary(details);
}

- (NSDictionary<NSString *, NSString *> *)getVolatileAccountDetails:(NSString *)accountId {
    auto details = libjami::getVolatileAccountDetails(toCppString(accountId));
    return toNSDictionary(details);
}

- (void)setAccountDetails:(NSString *)accountId
                  details:(NSDictionary<NSString *, NSString *> *)details {
    NSLog(@"[JamiBridge] setAccountDetails: %@", accountId);
    libjami::setAccountDetails(toCppString(accountId), toCppMap(details));
}

- (void)setAccountActive:(NSString *)accountId active:(BOOL)active {
    NSLog(@"[JamiBridge] setAccountActive: %@ active: %d", accountId, active);
    libjami::setAccountActive(toCppString(accountId), active);
}

- (void)updateProfile:(NSString *)accountId
          displayName:(NSString *)displayName
           avatarPath:(nullable NSString *)avatarPath {
    NSLog(@"[JamiBridge] updateProfile: %@ name: %@ avatarPath: %@", accountId, displayName, avatarPath);
    // Determine MIME type from file extension so the daemon broadcasts the profile to contacts.
    // An empty fileType tells the daemon to skip avatar processing entirely.
    NSString *fileType = @"";
    if (avatarPath && avatarPath.length > 0) {
        NSString *lower = [avatarPath lowercaseString];
        if ([lower hasSuffix:@".jpg"] || [lower hasSuffix:@".jpeg"]) {
            fileType = @"image/jpeg";
        } else if ([lower hasSuffix:@".png"]) {
            fileType = @"image/png";
        } else {
            fileType = @"image/jpeg"; // default for valid paths without recognized extension
        }
    }
    NSLog(@"[JamiBridge] updateProfile fileType: %@", fileType);
    libjami::updateProfile(toCppString(accountId),
                          toCppString(displayName),
                          avatarPath ? toCppString(avatarPath) : "",
                          toCppString(fileType),
                          0);  // flags
}

- (BOOL)registerName:(NSString *)accountId name:(NSString *)name password:(NSString *)password {
    NSLog(@"[JamiBridge] registerName: %@ name: %@", accountId, name);
    return libjami::registerName(toCppString(accountId), toCppString(name), "", toCppString(password));
}

- (nullable JBLookupResult *)lookupName:(NSString *)accountId name:(NSString *)name {
    NSLog(@"[JamiBridge] lookupName: %@", name);
    libjami::lookupName(toCppString(accountId), "", toCppString(name));
    // Result comes via RegisteredNameFound callback
    return nil;
}

- (nullable JBLookupResult *)lookupAddress:(NSString *)accountId address:(NSString *)address {
    NSLog(@"[JamiBridge] lookupAddress: %@", address);
    libjami::lookupAddress(toCppString(accountId), "", toCppString(address));
    // Result comes via RegisteredNameFound callback
    return nil;
}

// =============================================================================
// Contact Management
// =============================================================================

- (NSArray<JBContact *> *)getContacts:(NSString *)accountId {
    auto contacts = libjami::getContacts(toCppString(accountId));
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:contacts.size()];

    for (const auto& contactMap : contacts) {
        JBContact *contact = [[JBContact alloc] init];
        contact.uri = toNSString(contactMap.count("id") ? contactMap.at("id") : "");
        contact.displayName = toNSString(contactMap.count("displayName") ? contactMap.at("displayName") : "");
        contact.isConfirmed = contactMap.count("confirmed") && contactMap.at("confirmed") == "true";
        contact.isBanned = contactMap.count("banned") && contactMap.at("banned") == "true";
        [result addObject:contact];
    }

    return [result copy];
}

- (void)addContact:(NSString *)accountId uri:(NSString *)uri {
    FILE_LOG_I("JamiBridge", @"addContact: accountId=%@ uri=%@", accountId, uri);
    std::string accountIdStr = toCppString(accountId);
    std::string uriStr = toCppString(uri);
    FILE_LOG_I("JamiBridge", @"addContact: calling libjami::addContact with accountId=%s uri=%s", accountIdStr.c_str(), uriStr.c_str());
    libjami::addContact(accountIdStr, uriStr);
    FILE_LOG_I("JamiBridge", @"addContact: libjami::addContact completed");
}

- (void)removeContact:(NSString *)accountId uri:(NSString *)uri ban:(BOOL)ban {
    NSLog(@"[JamiBridge] removeContact: %@ uri: %@ ban: %d", accountId, uri, ban);
    libjami::removeContact(toCppString(accountId), toCppString(uri), ban);
}

- (NSDictionary<NSString *, NSString *> *)getContactDetails:(NSString *)accountId uri:(NSString *)uri {
    auto details = libjami::getContactDetails(toCppString(accountId), toCppString(uri));
    return toNSDictionary(details);
}

- (void)acceptTrustRequest:(NSString *)accountId uri:(NSString *)uri {
    NSLog(@"[JamiBridge] acceptTrustRequest: %@ uri: %@", accountId, uri);
    libjami::acceptTrustRequest(toCppString(accountId), toCppString(uri));
}

- (void)discardTrustRequest:(NSString *)accountId uri:(NSString *)uri {
    NSLog(@"[JamiBridge] discardTrustRequest: %@ uri: %@", accountId, uri);
    libjami::discardTrustRequest(toCppString(accountId), toCppString(uri));
}

- (NSArray<JBTrustRequest *> *)getTrustRequests:(NSString *)accountId {
    auto requests = libjami::getTrustRequests(toCppString(accountId));
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:requests.size()];

    for (const auto& reqMap : requests) {
        JBTrustRequest *request = [[JBTrustRequest alloc] init];
        request.from = toNSString(reqMap.count(Account::TrustRequest::FROM) ? reqMap.at(Account::TrustRequest::FROM) : "");
        request.conversationId = toNSString(reqMap.count(Account::TrustRequest::CONVERSATIONID) ? reqMap.at(Account::TrustRequest::CONVERSATIONID) : "");
        if (reqMap.count(Account::TrustRequest::RECEIVED)) {
            request.received = std::stoll(reqMap.at(Account::TrustRequest::RECEIVED));
        }
        [result addObject:request];
    }

    return [result copy];
}

- (void)subscribeBuddy:(NSString *)accountId uri:(NSString *)uri flag:(BOOL)flag {
    FILE_LOG_I("JamiBridge", @"subscribeBuddy: accountId=%@ uri=%@ flag=%d", accountId, uri, flag);
    libjami::subscribeBuddy(toCppString(accountId), toCppString(uri), flag);
    FILE_LOG_I("JamiBridge", @"subscribeBuddy: completed");
}

// =============================================================================
// Conversation Management
// =============================================================================

- (NSArray<NSString *> *)getConversations:(NSString *)accountId {
    auto conversations = libjami::getConversations(toCppString(accountId));
    return toNSArray(conversations);
}

- (NSString *)startConversation:(NSString *)accountId {
    FILE_LOG_I("JamiBridge", @"startConversation: accountId=%@", accountId);
    std::string accountIdStr = toCppString(accountId);
    FILE_LOG_I("JamiBridge", @"startConversation: calling libjami::startConversation");
    std::string conversationId = libjami::startConversation(accountIdStr);
    FILE_LOG_I("JamiBridge", @"startConversation: result=%s", conversationId.c_str());
    return toNSString(conversationId);
}

- (void)removeConversation:(NSString *)accountId conversationId:(NSString *)conversationId {
    NSLog(@"[JamiBridge] removeConversation: %@ conversationId: %@", accountId, conversationId);
    libjami::removeConversation(toCppString(accountId), toCppString(conversationId));
}

- (NSDictionary<NSString *, NSString *> *)getConversationInfo:(NSString *)accountId
                                               conversationId:(NSString *)conversationId {
    auto info = libjami::conversationInfos(toCppString(accountId), toCppString(conversationId));
    return toNSDictionary(info);
}

- (void)updateConversationInfo:(NSString *)accountId
                conversationId:(NSString *)conversationId
                          info:(NSDictionary<NSString *, NSString *> *)info {
    NSLog(@"[JamiBridge] updateConversationInfo: %@", conversationId);
    libjami::updateConversationInfos(toCppString(accountId), toCppString(conversationId), toCppMap(info));
}

- (NSArray<JBConversationMember *> *)getConversationMembers:(NSString *)accountId
                                             conversationId:(NSString *)conversationId {
    auto members = libjami::getConversationMembers(toCppString(accountId), toCppString(conversationId));
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:members.size()];

    for (const auto& memberMap : members) {
        JBConversationMember *member = [[JBConversationMember alloc] init];
        member.uri = toNSString(memberMap.count("uri") ? memberMap.at("uri") : "");

        std::string role = memberMap.count("role") ? memberMap.at("role") : "";
        if (role == "admin") {
            member.role = JBMemberRoleAdmin;
        } else if (role == "member") {
            member.role = JBMemberRoleMember;
        } else if (role == "invited") {
            member.role = JBMemberRoleInvited;
        } else if (role == "banned") {
            member.role = JBMemberRoleBanned;
        } else {
            member.role = JBMemberRoleMember;
        }

        [result addObject:member];
    }

    return [result copy];
}

- (void)addConversationMember:(NSString *)accountId
               conversationId:(NSString *)conversationId
                   contactUri:(NSString *)contactUri {
    FILE_LOG_I("JamiBridge", @"addConversationMember: accountId=%@ conversationId=%@ contactUri=%@", accountId, conversationId, contactUri);
    std::string accountIdStr = toCppString(accountId);
    std::string conversationIdStr = toCppString(conversationId);
    std::string contactUriStr = toCppString(contactUri);
    FILE_LOG_I("JamiBridge", @"addConversationMember: calling libjami::addConversationMember");
    libjami::addConversationMember(accountIdStr, conversationIdStr, contactUriStr);
    FILE_LOG_I("JamiBridge", @"addConversationMember: completed");
}

- (void)removeConversationMember:(NSString *)accountId
                  conversationId:(NSString *)conversationId
                      contactUri:(NSString *)contactUri {
    NSLog(@"[JamiBridge] removeConversationMember: %@ from %@", contactUri, conversationId);
    libjami::removeConversationMember(toCppString(accountId), toCppString(conversationId), toCppString(contactUri));
}

- (void)acceptConversationRequest:(NSString *)accountId conversationId:(NSString *)conversationId {
    NSLog(@"[JamiBridge] acceptConversationRequest: %@", conversationId);
    libjami::acceptConversationRequest(toCppString(accountId), toCppString(conversationId));
}

- (void)declineConversationRequest:(NSString *)accountId conversationId:(NSString *)conversationId {
    NSLog(@"[JamiBridge] declineConversationRequest: %@", conversationId);
    libjami::declineConversationRequest(toCppString(accountId), toCppString(conversationId));
}

- (NSArray<JBConversationRequest *> *)getConversationRequests:(NSString *)accountId {
    auto requests = libjami::getConversationRequests(toCppString(accountId));
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:requests.size()];

    for (const auto& reqMap : requests) {
        JBConversationRequest *request = [[JBConversationRequest alloc] init];
        request.conversationId = toNSString(reqMap.count("id") ? reqMap.at("id") : "");
        request.from = toNSString(reqMap.count("from") ? reqMap.at("from") : "");
        request.metadata = toNSDictionary(reqMap);
        [result addObject:request];
    }

    return [result copy];
}

// =============================================================================
// Messaging
// =============================================================================

- (NSString *)sendMessage:(NSString *)accountId
           conversationId:(NSString *)conversationId
                  message:(NSString *)message
                  replyTo:(nullable NSString *)replyTo {
    NSLog(@"[JamiBridge] sendMessage to %@: %@", conversationId, message);
    libjami::sendMessage(toCppString(accountId),
                        toCppString(conversationId),
                        toCppString(message),
                        replyTo ? toCppString(replyTo) : "",
                        0);
    return @"";  // Message ID comes via callback
}

- (int)loadConversationMessages:(NSString *)accountId
                 conversationId:(NSString *)conversationId
                    fromMessage:(NSString *)fromMessage
                          count:(int)count {
    NSLog(@"[JamiBridge] loadConversationMessages: %@ count: %d", conversationId, count);
    uint32_t requestId = libjami::loadConversation(toCppString(accountId),
                                                   toCppString(conversationId),
                                                   toCppString(fromMessage),
                                                   static_cast<size_t>(count));
    return static_cast<int>(requestId);
}

- (void)setIsComposing:(NSString *)accountId
        conversationId:(NSString *)conversationId
           isComposing:(BOOL)isComposing {
    NSLog(@"[JamiBridge] setIsComposing: %@ composing: %d", conversationId, isComposing);
    libjami::setIsComposing(toCppString(accountId), toCppString(conversationId), isComposing);
}

- (void)setMessageDisplayed:(NSString *)accountId
             conversationId:(NSString *)conversationId
                  messageId:(NSString *)messageId {
    NSLog(@"[JamiBridge] setMessageDisplayed: %@ message: %@", conversationId, messageId);
    libjami::setMessageDisplayed(toCppString(accountId), toCppString(conversationId), toCppString(messageId), 3);
}

// =============================================================================
// Calls
// =============================================================================

- (NSString *)placeCall:(NSString *)accountId uri:(NSString *)uri withVideo:(BOOL)withVideo {
    NSLog(@"[JamiBridge] placeCall to %@ video: %d", uri, withVideo);

    std::vector<std::map<std::string, std::string>> mediaList;

    // Audio media
    std::map<std::string, std::string> audio;
    audio["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO";
    audio["ENABLED"] = "true";
    audio["MUTED"] = "false";
    audio["SOURCE"] = "";
    mediaList.push_back(audio);

    // Video media (optional)
    if (withVideo) {
        std::map<std::string, std::string> video;
        video["MEDIA_TYPE"] = "MEDIA_TYPE_VIDEO";
        video["ENABLED"] = "true";
        video["MUTED"] = "false";
        video["SOURCE"] = "camera://0";
        mediaList.push_back(video);
    }

    std::string callId = libjami::placeCallWithMedia(toCppString(accountId), toCppString(uri), mediaList);
    return toNSString(callId);
}

- (void)acceptCall:(NSString *)accountId callId:(NSString *)callId withVideo:(BOOL)withVideo {
    NSLog(@"[JamiBridge] acceptCall: %@ video: %d", callId, withVideo);

    std::vector<std::map<std::string, std::string>> mediaList;

    // Audio media
    std::map<std::string, std::string> audio;
    audio["MEDIA_TYPE"] = "MEDIA_TYPE_AUDIO";
    audio["ENABLED"] = "true";
    audio["MUTED"] = "false";
    mediaList.push_back(audio);

    if (withVideo) {
        std::map<std::string, std::string> video;
        video["MEDIA_TYPE"] = "MEDIA_TYPE_VIDEO";
        video["ENABLED"] = "true";
        video["MUTED"] = "false";
        video["SOURCE"] = "camera://0";
        mediaList.push_back(video);
    }

    libjami::acceptWithMedia(toCppString(accountId), toCppString(callId), mediaList);
}

- (void)refuseCall:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] refuseCall: %@", callId);
    libjami::refuse(toCppString(accountId), toCppString(callId));
}

- (void)hangUp:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] hangUp: %@", callId);
    libjami::hangUp(toCppString(accountId), toCppString(callId));
}

- (void)holdCall:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] holdCall: %@", callId);
    libjami::hold(toCppString(accountId), toCppString(callId));
}

- (void)unholdCall:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] unholdCall: %@", callId);
    libjami::unhold(toCppString(accountId), toCppString(callId));
}

- (void)muteAudio:(NSString *)accountId callId:(NSString *)callId muted:(BOOL)muted {
    NSLog(@"[JamiBridge] muteAudio: %@ muted: %d", callId, muted);
    libjami::muteLocalMedia(toCppString(accountId), toCppString(callId), "MEDIA_TYPE_AUDIO", muted);
}

- (void)muteVideo:(NSString *)accountId callId:(NSString *)callId muted:(BOOL)muted {
    NSLog(@"[JamiBridge] muteVideo: %@ muted: %d", callId, muted);
    libjami::muteLocalMedia(toCppString(accountId), toCppString(callId), "MEDIA_TYPE_VIDEO", muted);
}

- (NSDictionary<NSString *, NSString *> *)getCallDetails:(NSString *)accountId callId:(NSString *)callId {
    auto details = libjami::getCallDetails(toCppString(accountId), toCppString(callId));
    return toNSDictionary(details);
}

- (NSArray<NSString *> *)getActiveCalls:(NSString *)accountId {
    auto calls = libjami::getCallList(toCppString(accountId));
    return toNSArray(calls);
}

- (void)switchCamera {
    NSLog(@"[JamiBridge] switchCamera");
    // iOS camera switching is handled at the application level
    // This would need to be implemented using iOS AVFoundation
}

- (void)switchAudioOutputUseSpeaker:(BOOL)useSpeaker {
    NSLog(@"[JamiBridge] switchAudioOutput speaker: %d", useSpeaker);
    // iOS audio routing is handled at the application level
    // This would need to be implemented using iOS AVAudioSession
}

// =============================================================================
// Conference Calls
// =============================================================================

- (NSString *)createConference:(NSString *)accountId
               participantUris:(NSArray<NSString *> *)participantUris {
    NSLog(@"[JamiBridge] createConference with %lu participants", (unsigned long)participantUris.count);
    libjami::createConfFromParticipantList(toCppString(accountId), toCppVector(participantUris));
    return @"";  // Conference ID comes via callback
}

- (void)joinParticipant:(NSString *)accountId
                 callId:(NSString *)callId
              accountId2:(NSString *)accountId2
                 callId2:(NSString *)callId2 {
    NSLog(@"[JamiBridge] joinParticipant: %@ with %@", callId, callId2);
    libjami::joinParticipant(toCppString(accountId), toCppString(callId),
                            toCppString(accountId2), toCppString(callId2));
}

- (void)addParticipantToConference:(NSString *)accountId
                            callId:(NSString *)callId
               conferenceAccountId:(NSString *)conferenceAccountId
                      conferenceId:(NSString *)conferenceId {
    NSLog(@"[JamiBridge] addParticipantToConference: %@ conference: %@", callId, conferenceId);
    libjami::addParticipant(toCppString(accountId), toCppString(callId),
                           toCppString(conferenceAccountId), toCppString(conferenceId));
}

- (void)hangUpConference:(NSString *)accountId conferenceId:(NSString *)conferenceId {
    NSLog(@"[JamiBridge] hangUpConference: %@", conferenceId);
    libjami::hangUpConference(toCppString(accountId), toCppString(conferenceId));
}

- (NSDictionary<NSString *, NSString *> *)getConferenceDetails:(NSString *)accountId
                                                  conferenceId:(NSString *)conferenceId {
    auto details = libjami::getConferenceDetails(toCppString(accountId), toCppString(conferenceId));
    return toNSDictionary(details);
}

- (NSArray<NSString *> *)getConferenceParticipants:(NSString *)accountId
                                      conferenceId:(NSString *)conferenceId {
    auto participants = libjami::getParticipantList(toCppString(accountId), toCppString(conferenceId));
    return toNSArray(participants);
}

- (NSArray<NSDictionary<NSString *, NSString *> *> *)getConferenceInfos:(NSString *)accountId
                                                           conferenceId:(NSString *)conferenceId {
    auto infos = libjami::getConferenceInfos(toCppString(accountId), toCppString(conferenceId));
    NSMutableArray *result = [NSMutableArray arrayWithCapacity:infos.size()];
    for (const auto& info : infos) {
        [result addObject:toNSDictionary(info)];
    }
    return [result copy];
}

- (void)setConferenceLayout:(NSString *)accountId
               conferenceId:(NSString *)conferenceId
                     layout:(JBConferenceLayout)layout {
    NSLog(@"[JamiBridge] setConferenceLayout: %@ layout: %ld", conferenceId, (long)layout);
    libjami::setConferenceLayout(toCppString(accountId), toCppString(conferenceId), static_cast<uint32_t>(layout));
}

- (void)muteConferenceParticipant:(NSString *)accountId
                     conferenceId:(NSString *)conferenceId
                   participantUri:(NSString *)participantUri
                            muted:(BOOL)muted {
    NSLog(@"[JamiBridge] muteConferenceParticipant: %@ muted: %d", participantUri, muted);
    libjami::muteParticipant(toCppString(accountId), toCppString(conferenceId),
                            toCppString(participantUri), muted);
}

- (void)hangUpConferenceParticipant:(NSString *)accountId
                       conferenceId:(NSString *)conferenceId
                     participantUri:(NSString *)participantUri
                           deviceId:(NSString *)deviceId {
    NSLog(@"[JamiBridge] hangUpConferenceParticipant: %@", participantUri);
    libjami::hangupParticipant(toCppString(accountId), toCppString(conferenceId),
                              toCppString(participantUri), toCppString(deviceId));
}

// =============================================================================
// File Transfer
// =============================================================================

- (NSString *)sendFile:(NSString *)accountId
        conversationId:(NSString *)conversationId
              filePath:(NSString *)filePath
           displayName:(NSString *)displayName {
    NSLog(@"[JamiBridge] sendFile: %@ name: %@", filePath, displayName);
    // File transfer in swarm conversations uses sendMessage with file:// URI
    std::string fileUri = "file://" + toCppString(filePath);
    libjami::sendMessage(toCppString(accountId), toCppString(conversationId), fileUri, "", 0);
    return @"";
}

- (void)acceptFileTransfer:(NSString *)accountId
            conversationId:(NSString *)conversationId
             interactionId:(NSString *)interactionId
                    fileId:(NSString *)fileId
           destinationPath:(NSString *)destinationPath {
    NSLog(@"[JamiBridge] acceptFileTransfer: account=%@ conv=%@ interaction=%@ fileId=%@ dest=%@",
          accountId, conversationId, interactionId, fileId, destinationPath);
    bool result = libjami::downloadFile(toCppString(accountId),
                                        toCppString(conversationId),
                                        toCppString(interactionId),
                                        toCppString(fileId),
                                        toCppString(destinationPath));
    NSLog(@"[JamiBridge] downloadFile returned: %@", result ? @"true" : @"false");
}

- (void)cancelFileTransfer:(NSString *)accountId
            conversationId:(NSString *)conversationId
                    fileId:(NSString *)fileId {
    NSLog(@"[JamiBridge] cancelFileTransfer: %@", fileId);
    libjami::cancelDataTransfer(toCppString(accountId),
                                toCppString(conversationId),
                                toCppString(fileId));
}

- (nullable JBFileTransferInfo *)getFileTransferInfo:(NSString *)accountId
                                      conversationId:(NSString *)conversationId
                                              fileId:(NSString *)fileId {
    std::string path;
    int64_t total = 0;
    int64_t progress = 0;
    libjami::DataTransferError err = libjami::fileTransferInfo(toCppString(accountId),
                                                               toCppString(conversationId),
                                                               toCppString(fileId),
                                                               path, total, progress);
    if (err != libjami::DataTransferError::success) {
        return nil;
    }
    JBFileTransferInfo *info = [[JBFileTransferInfo alloc] init];
    info.fileId = fileId;
    info.path = toNSString(path);
    info.displayName = [toNSString(path) lastPathComponent];
    info.totalSize = total;
    info.progress = progress;
    info.bytesPerSecond = 0;
    info.author = @"";
    info.flags = 0;
    return info;
}

// =============================================================================
// Video
// =============================================================================

- (NSArray<NSString *> *)getVideoDevices {
    // On iOS, video devices are handled by AVFoundation
    return @[@"camera://0", @"camera://1"];
}

- (NSString *)getCurrentVideoDevice {
    return @"camera://0";
}

- (void)setVideoDevice:(NSString *)deviceId {
    NSLog(@"[JamiBridge] setVideoDevice: %@", deviceId);
}

- (void)startVideo {
    NSLog(@"[JamiBridge] startVideo");
}

- (void)stopVideo {
    NSLog(@"[JamiBridge] stopVideo");
}

// =============================================================================
// Audio Settings
// =============================================================================

- (NSArray<NSString *> *)getAudioOutputDevices {
    auto devices = libjami::getAudioOutputDeviceList();
    return toNSArray(devices);
}

- (NSArray<NSString *> *)getAudioInputDevices {
    auto devices = libjami::getAudioInputDeviceList();
    return toNSArray(devices);
}

- (void)setAudioOutputDevice:(int)index {
    NSLog(@"[JamiBridge] setAudioOutputDevice: %d", index);
    libjami::setAudioOutputDevice(index);
}

- (void)setAudioInputDevice:(int)index {
    NSLog(@"[JamiBridge] setAudioInputDevice: %d", index);
    libjami::setAudioInputDevice(index);
}

@end
