# Jami REST Bridge API Specification

## Overview

The REST Bridge provides a way for the Kotlin/JS web client to communicate with libjami. Since libjami is a native C++ library that cannot run directly in a browser, a server-side bridge is required.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Web Browser                                  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Kotlin/JS Client                           │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │  │
│  │  │ DaemonBridge│──│   HTTP/WS   │──│ Event Listener (Flow)│  │  │
│  │  │   (jsMain)  │  │   Client    │  │                      │  │  │
│  │  └─────────────┘  └─────────────┘  └──────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                              │ HTTP POST (operations)
                              │ WebSocket (events)
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      REST Bridge Server                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     REST API Layer                            │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │  │
│  │  │   Express   │  │  WebSocket  │  │   Callback Handler   │  │  │
│  │  │   Routes    │  │   Server    │  │   (broadcasts events)│  │  │
│  │  └─────────────┘  └─────────────┘  └──────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                      libjami (native)                         │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

## Base URL

```
HTTP:      http://localhost:8080/api/v1
WebSocket: ws://localhost:8080/ws
```

## Authentication

All requests require an API key in the `Authorization` header:

```
Authorization: Bearer <api-key>
```

The API key is configured on the REST bridge server.

---

## REST API Endpoints

### Daemon Lifecycle

#### Initialize Daemon
```
POST /daemon/init
```

Request body:
```json
{
  "flags": ["DEBUG", "CONSOLE_LOG"]
}
```

Response:
```json
{
  "success": true
}
```

#### Start Daemon
```
POST /daemon/start
```

Response:
```json
{
  "success": true
}
```

#### Stop Daemon
```
POST /daemon/stop
```

Response:
```json
{
  "success": true
}
```

#### Get Status
```
GET /daemon/status
```

Response:
```json
{
  "running": true,
  "version": "1.0.0"
}
```

---

### Account Operations

#### List Accounts
```
GET /accounts
```

Response:
```json
{
  "accounts": ["acc123", "acc456"]
}
```

#### Get Account Details
```
GET /accounts/{accountId}
```

Response:
```json
{
  "accountId": "acc123",
  "details": {
    "Account.type": "RING",
    "Account.alias": "My Account",
    "Account.displayName": "John Doe"
  }
}
```

#### Create Account
```
POST /accounts
```

Request body:
```json
{
  "details": {
    "Account.type": "RING",
    "Account.alias": "My Account"
  }
}
```

Response:
```json
{
  "accountId": "acc789"
}
```

#### Update Account
```
PUT /accounts/{accountId}
```

Request body:
```json
{
  "details": {
    "Account.displayName": "New Name"
  }
}
```

#### Delete Account
```
DELETE /accounts/{accountId}
```

#### Get Account Template
```
GET /accounts/template/{accountType}
```

Response:
```json
{
  "template": {
    "Account.type": "RING",
    "Account.alias": "",
    "Account.enable": "true"
  }
}
```

---

### Call Operations

#### Place Call
```
POST /calls
```

Request body:
```json
{
  "accountId": "acc123",
  "uri": "jami:abc123def456",
  "mediaList": [
    {
      "MEDIA_TYPE": "MEDIA_TYPE_AUDIO",
      "ENABLED": "true",
      "MUTED": "false",
      "SOURCE": ""
    },
    {
      "MEDIA_TYPE": "MEDIA_TYPE_VIDEO",
      "ENABLED": "true",
      "MUTED": "false",
      "SOURCE": "camera://0"
    }
  ]
}
```

Response:
```json
{
  "callId": "call456"
}
```

#### Accept Call
```
POST /calls/{callId}/accept
```

Request body:
```json
{
  "accountId": "acc123",
  "mediaList": [...]
}
```

#### Hang Up
```
POST /calls/{callId}/hangup
```

Request body:
```json
{
  "accountId": "acc123"
}
```

#### Hold/Unhold
```
POST /calls/{callId}/hold
POST /calls/{callId}/unhold
```

Request body:
```json
{
  "accountId": "acc123"
}
```

#### Mute Media
```
POST /calls/{callId}/mute
```

Request body:
```json
{
  "accountId": "acc123",
  "mediaType": "MEDIA_TYPE_AUDIO",
  "mute": true
}
```

#### Get Call Details
```
GET /accounts/{accountId}/calls/{callId}
```

Response:
```json
{
  "callId": "call456",
  "state": "CURRENT",
  "peerUri": "jami:abc123def456",
  "peerNumber": "abc123def456"
}
```

---

### Conversation Operations

#### List Conversations
```
GET /accounts/{accountId}/conversations
```

Response:
```json
{
  "conversations": ["conv123", "conv456"]
}
```

#### Get Conversation Info
```
GET /accounts/{accountId}/conversations/{conversationId}
```

Response:
```json
{
  "conversationId": "conv123",
  "info": {
    "title": "Chat with Alice",
    "mode": "0"
  },
  "members": [
    {"uri": "jami:abc", "role": "admin"},
    {"uri": "jami:def", "role": "member"}
  ]
}
```

#### Start Conversation
```
POST /accounts/{accountId}/conversations
```

Response:
```json
{
  "conversationId": "conv789"
}
```

#### Send Message
```
POST /accounts/{accountId}/conversations/{conversationId}/messages
```

Request body:
```json
{
  "message": "Hello, world!",
  "replyTo": "",
  "flag": 0
}
```

#### Load Messages
```
GET /accounts/{accountId}/conversations/{conversationId}/messages
```

Query parameters:
- `from`: Message ID to load from (pagination)
- `size`: Number of messages (default: 20)

Response:
```json
{
  "messages": [
    {
      "id": "msg123",
      "type": "text/plain",
      "body": {"text": "Hello!"},
      "author": "jami:abc",
      "timestamp": "1699999999"
    }
  ]
}
```

#### Delete Conversation
```
DELETE /accounts/{accountId}/conversations/{conversationId}
```

---

### Contact Operations

#### List Contacts
```
GET /accounts/{accountId}/contacts
```

Response:
```json
{
  "contacts": [
    {
      "uri": "jami:abc123",
      "added": "1699999999",
      "confirmed": "true"
    }
  ]
}
```

#### Add Contact
```
POST /accounts/{accountId}/contacts
```

Request body:
```json
{
  "uri": "jami:abc123"
}
```

#### Remove Contact
```
DELETE /accounts/{accountId}/contacts/{uri}
```

Query parameters:
- `ban`: boolean (default: false)

#### Get Contact Details
```
GET /accounts/{accountId}/contacts/{uri}
```

---

### Name Lookup

#### Lookup Name
```
GET /accounts/{accountId}/ns/name/{name}
```

Response:
```json
{
  "found": true,
  "address": "abc123def456789",
  "name": "alice"
}
```

#### Lookup Address
```
GET /accounts/{accountId}/ns/address/{address}
```

Response:
```json
{
  "found": true,
  "address": "abc123def456789",
  "name": "alice"
}
```

#### Register Name
```
POST /accounts/{accountId}/ns/register
```

Request body:
```json
{
  "name": "alice",
  "scheme": "",
  "password": "secret"
}
```

---

### File Transfer

#### Send File
```
POST /accounts/{accountId}/conversations/{conversationId}/files
```

Request body (multipart/form-data):
- `file`: File content
- `displayName`: Display name for the file
- `parent`: Parent message ID (optional)

#### Download File
```
GET /accounts/{accountId}/conversations/{conversationId}/files/{fileId}
```

Query parameters:
- `interactionId`: Interaction ID

Response: File content with appropriate Content-Type header

#### Get Transfer Info
```
GET /accounts/{accountId}/conversations/{conversationId}/files/{fileId}/info
```

Response:
```json
{
  "fileId": "file123",
  "path": "/path/to/file",
  "displayName": "document.pdf",
  "totalSize": 1024000,
  "progress": 512000
}
```

#### Cancel Transfer
```
DELETE /accounts/{accountId}/conversations/{conversationId}/files/{fileId}
```

---

## WebSocket Events

Connect to the WebSocket endpoint to receive real-time events:

```javascript
const ws = new WebSocket('ws://localhost:8080/ws');
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Event:', data.type, data.payload);
};
```

### Event Format

All events follow this format:
```json
{
  "type": "EVENT_TYPE",
  "payload": { ... }
}
```

### Account Events

#### AccountsChanged
```json
{
  "type": "ACCOUNTS_CHANGED",
  "payload": {}
}
```

#### RegistrationStateChanged
```json
{
  "type": "REGISTRATION_STATE_CHANGED",
  "payload": {
    "accountId": "acc123",
    "state": "REGISTERED",
    "code": 200,
    "details": ""
  }
}
```

#### KnownDevicesChanged
```json
{
  "type": "KNOWN_DEVICES_CHANGED",
  "payload": {
    "accountId": "acc123",
    "devices": {
      "dev1": "Device Name"
    }
  }
}
```

### Call Events

#### CallStateChanged
```json
{
  "type": "CALL_STATE_CHANGED",
  "payload": {
    "accountId": "acc123",
    "callId": "call456",
    "state": "CURRENT",
    "code": 0
  }
}
```

#### IncomingCall
```json
{
  "type": "INCOMING_CALL",
  "payload": {
    "accountId": "acc123",
    "callId": "call456",
    "from": "jami:abc123",
    "mediaList": [...]
  }
}
```

#### MediaChangeRequested
```json
{
  "type": "MEDIA_CHANGE_REQUESTED",
  "payload": {
    "accountId": "acc123",
    "callId": "call456",
    "mediaList": [...]
  }
}
```

### Conversation Events

#### ConversationReady
```json
{
  "type": "CONVERSATION_READY",
  "payload": {
    "accountId": "acc123",
    "conversationId": "conv456"
  }
}
```

#### MessageReceived
```json
{
  "type": "MESSAGE_RECEIVED",
  "payload": {
    "accountId": "acc123",
    "conversationId": "conv456",
    "message": {
      "id": "msg789",
      "type": "text/plain",
      "body": {"text": "Hello!"},
      "author": "jami:abc",
      "timestamp": "1699999999",
      "linearizedParent": "",
      "reactions": [],
      "editions": [],
      "status": {}
    }
  }
}
```

#### ConversationLoaded
```json
{
  "type": "CONVERSATION_LOADED",
  "payload": {
    "accountId": "acc123",
    "conversationId": "conv456",
    "messages": [...]
  }
}
```

#### ConversationRequestReceived
```json
{
  "type": "CONVERSATION_REQUEST_RECEIVED",
  "payload": {
    "accountId": "acc123",
    "conversationId": "conv456",
    "metadata": {
      "from": "jami:abc"
    }
  }
}
```

### Contact Events

#### ContactAdded
```json
{
  "type": "CONTACT_ADDED",
  "payload": {
    "accountId": "acc123",
    "uri": "jami:abc",
    "confirmed": true
  }
}
```

#### ContactRemoved
```json
{
  "type": "CONTACT_REMOVED",
  "payload": {
    "accountId": "acc123",
    "uri": "jami:abc",
    "banned": false
  }
}
```

### Name Service Events

#### NameRegistrationEnded
```json
{
  "type": "NAME_REGISTRATION_ENDED",
  "payload": {
    "accountId": "acc123",
    "state": 0,
    "name": "alice"
  }
}
```

#### RegisteredNameFound
```json
{
  "type": "REGISTERED_NAME_FOUND",
  "payload": {
    "accountId": "acc123",
    "state": 0,
    "address": "abc123def456",
    "name": "alice"
  }
}
```

### Data Transfer Events

#### DataTransferEvent
```json
{
  "type": "DATA_TRANSFER_EVENT",
  "payload": {
    "accountId": "acc123",
    "conversationId": "conv456",
    "interactionId": "int789",
    "fileId": "file123",
    "code": 3
  }
}
```

---

## Error Responses

All errors follow this format:

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Account not found",
    "details": {
      "accountId": "invalid_id"
    }
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_REQUEST` | 400 | Malformed request |
| `UNAUTHORIZED` | 401 | Invalid or missing API key |
| `NOT_FOUND` | 404 | Resource not found |
| `DAEMON_ERROR` | 500 | libjami operation failed |
| `NOT_RUNNING` | 503 | Daemon not initialized |

---

## Implementation Notes

### Server Implementation

The REST bridge server can be implemented in:
- **Node.js** with Express (recommended for ease of development)
- **Rust** with Actix-web (for performance)
- **C++** directly in libjami (for tight integration)

The server must:
1. Link against libjami
2. Register daemon callbacks and broadcast events via WebSocket
3. Handle HTTP requests and translate to libjami calls
4. Support multiple WebSocket clients

### Client Implementation (jsMain)

The Kotlin/JS DaemonBridge implementation will:
1. Use `ktor-client-js` for HTTP requests
2. Use browser's native WebSocket for events
3. Convert WebSocket events to Kotlin Flow emissions
4. Handle reconnection on WebSocket disconnect

### Video/Audio Considerations

For the web platform, actual media (audio/video) is handled separately via WebRTC:
- libjami handles signaling through the REST bridge
- The browser handles media capture and rendering via WebRTC APIs
- ICE candidates are exchanged through conversation messages

This specification covers the signaling and control plane only.
