# Java-Based Communication System

A real-time company messaging application built with Java. Employees can send direct messages and group chats to anyone in the organisation, whether they are online or offline.

## Features

- **Login** — authenticate with your employee ID and password
- **Direct & group messaging** — start a chat with one or multiple colleagues at once
- **Offline messaging** — messages are saved and visible when the recipient next logs in
- **Real-time delivery** — messages are pushed instantly to online users without refreshing
- **Online users list** — see who is currently connected
- **Conversation history** — all past messages are persisted across sessions
- **Latest conversations on top** — sidebar is sorted by most recent message
- **Unread indicators** — conversations with new messages are highlighted in bold
- **IT admin view** — IT users can view all conversations across the organisation

## Getting Started

### Prerequisites

- Java 11 or later

### Running the application

1. **Start the server**

   ```
   javac src/*.java -d bin
   java -cp bin Server
   ```

2. **Start a client** (run once per user, in a separate terminal)

   ```
   java -cp bin Client
   ```

3. Log in with credentials from `EmpInfo.txt`

## Project Structure

```
├── src/
│   ├── Server.java           # Accepts connections, spawns a thread per client
│   ├── Client.java           # Manages the socket connection and message routing
│   ├── GUI.java              # Swing UI — login window and main chat window
│   ├── Message.java          # Serializable message object passed over the wire
│   ├── Conversation.java     # Holds a conversation's members and message history
│   ├── ConversationList.java # Serializable list of conversations
│   ├── User.java             # Represents an authenticated employee
│   ├── Role.java             # Employee or ITUser
│   └── Status.java           # Message status (request, success, fail, delivered)
├── EmpInfo.txt               # Employee credentials and roles
└── ConversationHistory.txt   # Persisted message history
```

## Architecture

The server runs a single `ServerSocket` on port 1200. For every client that connects, it spawns a new thread running `ClientHandler`. All active sessions share a `ConcurrentHashMap<String, ObjectOutputStream>` so any handler can push a message directly to any online user.

The client uses two threads after login:
- **MessageListener** — always blocked on the socket, routes incoming objects to either the UI callback (live chat messages) or a `BlockingQueue` (responses to requests)
- **EDT (Swing)** — all UI updates happen here via `SwingWorker` so the interface never freezes during network calls

Conversation identity is based on the sorted, uppercased list of member names, so the same conversation is always matched correctly regardless of who initiates it.

## Employee Credentials

| Username | Password | Name     | Role     |
|----------|----------|----------|----------|
| Sus1     | pas1     | Sushant  | Employee |
| Tim2     | paop     | Tim      | Employee |
| Jeze3    | house    | Jezelle  | Employee |
| Pol45    | 123      | Polly    | IT       |
| Bil5     | 321      | Billy    | Employee |
| Ric4     | PPP56    | Rick     | Employee |
| m432     | MaxisAwesome | Max  | Employee |
| Mark0    | passw    | Mark     | IT       |
