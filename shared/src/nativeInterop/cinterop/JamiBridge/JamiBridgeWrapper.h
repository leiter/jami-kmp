//
//  JamiBridgeWrapper.h
//  GetTogether
//
//  Objective-C header for Kotlin cinterop
//  This provides the bridge between Swift/ObjC and Kotlin Multiplatform
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

// =============================================================================
// Enums
// =============================================================================

typedef NS_ENUM(NSInteger, JBRegistrationState) {
    JBRegistrationStateUnregistered,
    JBRegistrationStateTrying,
    JBRegistrationStateRegistered,
    JBRegistrationStateErrorGeneric,
    JBRegistrationStateErrorAuth,
    JBRegistrationStateErrorNetwork,
    JBRegistrationStateErrorHost,
    JBRegistrationStateErrorServiceUnavailable,
    JBRegistrationStateErrorNeedMigration,
    JBRegistrationStateInitializing
};

typedef NS_ENUM(NSInteger, JBCallState) {
    JBCallStateInactive,
    JBCallStateIncoming,
    JBCallStateConnecting,
    JBCallStateRinging,
    JBCallStateCurrent,
    JBCallStateHungup,
    JBCallStateBusy,
    JBCallStateFailure,
    JBCallStateHold,
    JBCallStateUnhold,
    JBCallStateOver
};

typedef NS_ENUM(NSInteger, JBLookupState) {
    JBLookupStateSuccess,
    JBLookupStateNotFound,
    JBLookupStateInvalid,
    JBLookupStateError
};

typedef NS_ENUM(NSInteger, JBMemberRole) {
    JBMemberRoleAdmin,
    JBMemberRoleMember,
    JBMemberRoleInvited,
    JBMemberRoleBanned
};

typedef NS_ENUM(NSInteger, JBConferenceLayout) {
    JBConferenceLayoutGrid,
    JBConferenceLayoutOneBig,
    JBConferenceLayoutOneBigSmall
};

typedef NS_ENUM(NSInteger, JBMemberEventType) {
    JBMemberEventTypeJoin,
    JBMemberEventTypeLeave,
    JBMemberEventTypeBan,
    JBMemberEventTypeUnban
};

// =============================================================================
// Data Classes
// =============================================================================

@interface JBContact : NSObject
@property (nonatomic, copy) NSString *uri;
@property (nonatomic, copy) NSString *displayName;
@property (nonatomic, copy, nullable) NSString *avatarPath;
@property (nonatomic, assign) BOOL isConfirmed;
@property (nonatomic, assign) BOOL isBanned;
@end

@interface JBTrustRequest : NSObject
@property (nonatomic, copy) NSString *from;
@property (nonatomic, copy) NSString *conversationId;
@property (nonatomic, strong) NSData *payload;
@property (nonatomic, assign) int64_t received;
@end

@interface JBConversationMember : NSObject
@property (nonatomic, copy) NSString *uri;
@property (nonatomic, assign) JBMemberRole role;
@end

@interface JBConversationRequest : NSObject
@property (nonatomic, copy) NSString *conversationId;
@property (nonatomic, copy) NSString *from;
@property (nonatomic, strong) NSDictionary<NSString *, NSString *> *metadata;
@property (nonatomic, assign) int64_t received;
@end

@interface JBLookupResult : NSObject
@property (nonatomic, copy) NSString *address;
@property (nonatomic, copy) NSString *name;
@property (nonatomic, assign) JBLookupState state;
@end

@interface JBFileTransferInfo : NSObject
@property (nonatomic, copy) NSString *fileId;
@property (nonatomic, copy) NSString *path;
@property (nonatomic, copy) NSString *displayName;
@property (nonatomic, assign) int64_t totalSize;
@property (nonatomic, assign) int64_t progress;
@property (nonatomic, assign) int64_t bytesPerSecond;
@property (nonatomic, copy) NSString *author;
@property (nonatomic, assign) int flags;
@end

@interface JBSwarmMessage : NSObject
@property (nonatomic, copy) NSString *messageId;
@property (nonatomic, copy) NSString *type;
@property (nonatomic, copy) NSString *author;
@property (nonatomic, strong) NSDictionary<NSString *, NSString *> *body;
@property (nonatomic, strong) NSArray<NSDictionary<NSString *, NSString *> *> *reactions;
@property (nonatomic, assign) int64_t timestamp;
@property (nonatomic, copy, nullable) NSString *replyTo;
@property (nonatomic, strong) NSDictionary<NSString *, NSNumber *> *status;
@end

// =============================================================================
// Delegate Protocol - Callbacks from daemon to Kotlin
// =============================================================================

@protocol JamiBridgeDelegate <NSObject>

@optional

// Account Events
- (void)onRegistrationStateChanged:(NSString *)accountId
                             state:(JBRegistrationState)state
                              code:(int)code
                            detail:(NSString *)detail;

- (void)onAccountDetailsChanged:(NSString *)accountId
                        details:(NSDictionary<NSString *, NSString *> *)details;

- (void)onProfileReceived:(NSString *)accountId
                     from:(NSString *)from
              displayName:(NSString *)displayName
               avatarPath:(nullable NSString *)avatarPath;

- (void)onNameRegistrationEnded:(NSString *)accountId
                          state:(int)state
                           name:(NSString *)name;

- (void)onRegisteredNameFound:(NSString *)accountId
                        state:(JBLookupState)state
                      address:(NSString *)address
                         name:(NSString *)name;

- (void)onKnownDevicesChanged:(NSString *)accountId
                      devices:(NSDictionary<NSString *, NSString *> *)devices;

// Call Events
- (void)onIncomingCall:(NSString *)accountId
                callId:(NSString *)callId
                peerId:(NSString *)peerId
       peerDisplayName:(NSString *)peerDisplayName
              hasVideo:(BOOL)hasVideo;

- (void)onCallStateChanged:(NSString *)accountId
                    callId:(NSString *)callId
                     state:(JBCallState)state
                      code:(int)code;

- (void)onMediaChangeRequested:(NSString *)accountId
                        callId:(NSString *)callId
                     mediaList:(NSArray<NSDictionary<NSString *, NSString *> *> *)mediaList;

- (void)onAudioMuted:(NSString *)callId muted:(BOOL)muted;

- (void)onVideoMuted:(NSString *)callId muted:(BOOL)muted;

- (void)onConferenceCreated:(NSString *)accountId
             conversationId:(NSString *)conversationId
               conferenceId:(NSString *)conferenceId;

- (void)onConferenceChanged:(NSString *)accountId
               conferenceId:(NSString *)conferenceId
                      state:(NSString *)state;

- (void)onConferenceRemoved:(NSString *)accountId
               conferenceId:(NSString *)conferenceId;

- (void)onConferenceInfoUpdated:(NSString *)conferenceId
               participantInfos:(NSArray<NSDictionary<NSString *, NSString *> *> *)participantInfos;

// Conversation Events
- (void)onConversationReady:(NSString *)accountId
             conversationId:(NSString *)conversationId;

- (void)onConversationRemoved:(NSString *)accountId
               conversationId:(NSString *)conversationId;

- (void)onConversationRequestReceived:(NSString *)accountId
                       conversationId:(NSString *)conversationId
                             metadata:(NSDictionary<NSString *, NSString *> *)metadata;

- (void)onMessageReceived:(NSString *)accountId
           conversationId:(NSString *)conversationId
                  message:(JBSwarmMessage *)message;

- (void)onMessageUpdated:(NSString *)accountId
          conversationId:(NSString *)conversationId
                 message:(JBSwarmMessage *)message;

- (void)onMessagesLoaded:(int)requestId
               accountId:(NSString *)accountId
          conversationId:(NSString *)conversationId
                messages:(NSArray<JBSwarmMessage *> *)messages;

- (void)onConversationMemberEvent:(NSString *)accountId
                   conversationId:(NSString *)conversationId
                        memberUri:(NSString *)memberUri
                            event:(JBMemberEventType)event;

- (void)onComposingStatusChanged:(NSString *)accountId
                  conversationId:(NSString *)conversationId
                            from:(NSString *)from
                     isComposing:(BOOL)isComposing;

- (void)onConversationProfileUpdated:(NSString *)accountId
                      conversationId:(NSString *)conversationId
                             profile:(NSDictionary<NSString *, NSString *> *)profile;

- (void)onReactionAdded:(NSString *)accountId
         conversationId:(NSString *)conversationId
              messageId:(NSString *)messageId
               reaction:(NSDictionary<NSString *, NSString *> *)reaction;

- (void)onReactionRemoved:(NSString *)accountId
           conversationId:(NSString *)conversationId
                messageId:(NSString *)messageId
               reactionId:(NSString *)reactionId;

// Contact Events
- (void)onContactAdded:(NSString *)accountId
                   uri:(NSString *)uri
             confirmed:(BOOL)confirmed;

- (void)onContactRemoved:(NSString *)accountId
                     uri:(NSString *)uri
                  banned:(BOOL)banned;

- (void)onIncomingTrustRequest:(NSString *)accountId
                conversationId:(NSString *)conversationId
                          from:(NSString *)from
                       payload:(NSData *)payload
                      received:(int64_t)received;

- (void)onPresenceChanged:(NSString *)accountId
                      uri:(NSString *)uri
                 isOnline:(BOOL)isOnline;

@end

// =============================================================================
// Main Bridge Interface
// =============================================================================

@interface JamiBridgeWrapper : NSObject

/// Singleton instance
+ (instancetype)shared;

/// Delegate for receiving callbacks
@property (nonatomic, weak, nullable) id<JamiBridgeDelegate> delegate;

// =========================================================================
// Daemon Lifecycle (4 methods)
// =========================================================================

- (void)initDaemonWithDataPath:(NSString *)dataPath;
- (void)startDaemon;
- (void)stopDaemon;
- (BOOL)isDaemonRunning;

// =========================================================================
// Account Management (14 methods)
// =========================================================================

- (NSString *)createAccountWithDisplayName:(NSString *)displayName
                                  password:(NSString *)password;

- (NSString *)importAccountFromArchive:(NSString *)archivePath
                              password:(NSString *)password;

- (BOOL)exportAccount:(NSString *)accountId
    toDestinationPath:(NSString *)destinationPath
         withPassword:(NSString *)password;

- (void)deleteAccount:(NSString *)accountId;

- (NSArray<NSString *> *)getAccountIds;

- (NSDictionary<NSString *, NSString *> *)getAccountDetails:(NSString *)accountId;

- (NSDictionary<NSString *, NSString *> *)getVolatileAccountDetails:(NSString *)accountId;

- (void)setAccountDetails:(NSString *)accountId
                  details:(NSDictionary<NSString *, NSString *> *)details;

- (void)setAccountActive:(NSString *)accountId active:(BOOL)active;

- (void)updateProfile:(NSString *)accountId
          displayName:(NSString *)displayName
           avatarPath:(nullable NSString *)avatarPath;

- (BOOL)registerName:(NSString *)accountId
                name:(NSString *)name
            password:(NSString *)password;

- (nullable JBLookupResult *)lookupName:(NSString *)accountId name:(NSString *)name;

- (nullable JBLookupResult *)lookupAddress:(NSString *)accountId address:(NSString *)address;

// =========================================================================
// Contact Management (7 methods)
// =========================================================================

- (NSArray<JBContact *> *)getContacts:(NSString *)accountId;

- (void)addContact:(NSString *)accountId uri:(NSString *)uri;

- (void)removeContact:(NSString *)accountId uri:(NSString *)uri ban:(BOOL)ban;

- (NSDictionary<NSString *, NSString *> *)getContactDetails:(NSString *)accountId
                                                        uri:(NSString *)uri;

- (void)acceptTrustRequest:(NSString *)accountId uri:(NSString *)uri;

- (void)discardTrustRequest:(NSString *)accountId uri:(NSString *)uri;

- (NSArray<JBTrustRequest *> *)getTrustRequests:(NSString *)accountId;

- (void)subscribeBuddy:(NSString *)accountId uri:(NSString *)uri flag:(BOOL)flag;

// =========================================================================
// Conversation Management (11 methods)
// =========================================================================

- (NSArray<NSString *> *)getConversations:(NSString *)accountId;

- (NSString *)startConversation:(NSString *)accountId;

- (void)removeConversation:(NSString *)accountId conversationId:(NSString *)conversationId;

- (NSDictionary<NSString *, NSString *> *)getConversationInfo:(NSString *)accountId
                                               conversationId:(NSString *)conversationId;

- (void)updateConversationInfo:(NSString *)accountId
                conversationId:(NSString *)conversationId
                          info:(NSDictionary<NSString *, NSString *> *)info;

- (NSArray<JBConversationMember *> *)getConversationMembers:(NSString *)accountId
                                             conversationId:(NSString *)conversationId;

- (void)addConversationMember:(NSString *)accountId
               conversationId:(NSString *)conversationId
                   contactUri:(NSString *)contactUri;

- (void)removeConversationMember:(NSString *)accountId
                  conversationId:(NSString *)conversationId
                      contactUri:(NSString *)contactUri;

- (void)acceptConversationRequest:(NSString *)accountId
                   conversationId:(NSString *)conversationId;

- (void)declineConversationRequest:(NSString *)accountId
                    conversationId:(NSString *)conversationId;

- (NSArray<JBConversationRequest *> *)getConversationRequests:(NSString *)accountId;

// =========================================================================
// Messaging (4 methods)
// =========================================================================

- (NSString *)sendMessage:(NSString *)accountId
           conversationId:(NSString *)conversationId
                  message:(NSString *)message
                  replyTo:(nullable NSString *)replyTo;

- (int)loadConversationMessages:(NSString *)accountId
                 conversationId:(NSString *)conversationId
                    fromMessage:(NSString *)fromMessage
                          count:(int)count;

- (void)setIsComposing:(NSString *)accountId
        conversationId:(NSString *)conversationId
           isComposing:(BOOL)isComposing;

- (void)setMessageDisplayed:(NSString *)accountId
             conversationId:(NSString *)conversationId
                  messageId:(NSString *)messageId;

// =========================================================================
// Calls (12 methods)
// =========================================================================

- (NSString *)placeCall:(NSString *)accountId uri:(NSString *)uri withVideo:(BOOL)withVideo;

- (void)acceptCall:(NSString *)accountId callId:(NSString *)callId withVideo:(BOOL)withVideo;

- (void)refuseCall:(NSString *)accountId callId:(NSString *)callId;

- (void)hangUp:(NSString *)accountId callId:(NSString *)callId;

- (void)holdCall:(NSString *)accountId callId:(NSString *)callId;

- (void)unholdCall:(NSString *)accountId callId:(NSString *)callId;

- (void)muteAudio:(NSString *)accountId callId:(NSString *)callId muted:(BOOL)muted;

- (void)muteVideo:(NSString *)accountId callId:(NSString *)callId muted:(BOOL)muted;

- (NSDictionary<NSString *, NSString *> *)getCallDetails:(NSString *)accountId
                                                  callId:(NSString *)callId;

- (NSArray<NSString *> *)getActiveCalls:(NSString *)accountId;

- (void)switchCamera;

- (void)switchAudioOutputUseSpeaker:(BOOL)useSpeaker;

// =========================================================================
// Conference Calls (10 methods)
// =========================================================================

- (NSString *)createConference:(NSString *)accountId
               participantUris:(NSArray<NSString *> *)participantUris;

- (void)joinParticipant:(NSString *)accountId
                 callId:(NSString *)callId
              accountId2:(NSString *)accountId2
                 callId2:(NSString *)callId2;

- (void)addParticipantToConference:(NSString *)accountId
                            callId:(NSString *)callId
               conferenceAccountId:(NSString *)conferenceAccountId
                      conferenceId:(NSString *)conferenceId;

- (void)hangUpConference:(NSString *)accountId conferenceId:(NSString *)conferenceId;

- (NSDictionary<NSString *, NSString *> *)getConferenceDetails:(NSString *)accountId
                                                  conferenceId:(NSString *)conferenceId;

- (NSArray<NSString *> *)getConferenceParticipants:(NSString *)accountId
                                      conferenceId:(NSString *)conferenceId;

- (NSArray<NSDictionary<NSString *, NSString *> *> *)getConferenceInfos:(NSString *)accountId
                                                           conferenceId:(NSString *)conferenceId;

- (void)setConferenceLayout:(NSString *)accountId
               conferenceId:(NSString *)conferenceId
                     layout:(JBConferenceLayout)layout;

- (void)muteConferenceParticipant:(NSString *)accountId
                     conferenceId:(NSString *)conferenceId
                   participantUri:(NSString *)participantUri
                            muted:(BOOL)muted;

- (void)hangUpConferenceParticipant:(NSString *)accountId
                       conferenceId:(NSString *)conferenceId
                     participantUri:(NSString *)participantUri
                           deviceId:(NSString *)deviceId;

// =========================================================================
// File Transfer (4 methods)
// =========================================================================

- (NSString *)sendFile:(NSString *)accountId
        conversationId:(NSString *)conversationId
              filePath:(NSString *)filePath
           displayName:(NSString *)displayName;

- (void)acceptFileTransfer:(NSString *)accountId
            conversationId:(NSString *)conversationId
             interactionId:(NSString *)interactionId
                    fileId:(NSString *)fileId
           destinationPath:(NSString *)destinationPath;

- (void)cancelFileTransfer:(NSString *)accountId
            conversationId:(NSString *)conversationId
                    fileId:(NSString *)fileId;

- (nullable JBFileTransferInfo *)getFileTransferInfo:(NSString *)accountId
                                      conversationId:(NSString *)conversationId
                                              fileId:(NSString *)fileId;

// =========================================================================
// Video (5 methods)
// =========================================================================

- (NSArray<NSString *> *)getVideoDevices;
- (NSString *)getCurrentVideoDevice;
- (void)setVideoDevice:(NSString *)deviceId;
- (void)startVideo;
- (void)stopVideo;

// =========================================================================
// Audio Settings (4 methods)
// =========================================================================

- (NSArray<NSString *> *)getAudioOutputDevices;
- (NSArray<NSString *> *)getAudioInputDevices;
- (void)setAudioOutputDevice:(int)index;
- (void)setAudioInputDevice:(int)index;

@end

NS_ASSUME_NONNULL_END
