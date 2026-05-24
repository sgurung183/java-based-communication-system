import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class Client {
    private User user;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final BlockingQueue<Object> serverMessages = new LinkedBlockingQueue<>();
    private volatile Consumer<Message> messageCallback;

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Client client = new Client();
        client.runClientLoop();
    }

    public void runClientLoop() throws IOException, ClassNotFoundException {
        InetAddress localhost = InetAddress.getLocalHost();
        socket = new Socket(localhost.getHostAddress().trim(), 1200);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        boolean loggedIn = false;
        while (!loggedIn) {
            GUI.loginWindow(out, in);
            Message serverMessage = (Message) in.readObject();
            GUI.responseMessage(serverMessage.getContent());
            if (serverMessage.getStatus().equals(Status.success)) {
                loggedIn = true;
            }
        }

        user = (User) in.readObject();

        Thread listenerThread = new Thread(new MessageListener());
        listenerThread.setDaemon(true);
        listenerThread.start();

        SwingUtilities.invokeLater(() -> GUI.showMainWindow(user, this));
    }

    // --- Public API for GUI (METHODS FOR GUI TO TALK TO THE SERVER) ---

    public User getUser() {
        return user;
    }

    public void setMessageCallback(Consumer<Message> callback) {
        this.messageCallback = callback;
    }

    public synchronized ConversationList requestConversations() throws Exception {
        out.writeObject(new Message(user, "viewConversationsRequest", Status.request));
        out.flush();
        return (ConversationList) serverMessages.take();
    }

    public synchronized ConversationList requestAllConversations() throws Exception {
        out.writeObject(new Message(user, "viewAllConversationsRequest", Status.request));
        out.flush();
        return (ConversationList) serverMessages.take();
    }

    public synchronized List<User> requestAllUsers() throws Exception {
        out.writeObject(new Message(user, "getAllUsersRequest", Status.request));
        out.flush();
        return (List<User>) serverMessages.take();
    }

    public synchronized List<User> requestOnlineUsers() throws Exception {
        out.writeObject(new Message(user, "viewOnlineRequest", Status.request));
        out.flush();
        return (List<User>) serverMessages.take();
    }

    public synchronized void sendMessage(List<String> recipients, String content) throws Exception {
        out.writeObject(new Message(user, "sendMessageRequest", Status.request));
        out.flush();
        List<String> upper = new ArrayList<>();
        for (String r : recipients) upper.add(r.trim().toUpperCase());
        out.writeObject(new Message(user, upper, content, Status.request));
        out.flush();
    }

    public void logout() {
        try {
            synchronized (this) {
                out.writeObject(new Message(user, "logOutRequest", Status.request));
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // --- Background listener: routes push messages to GUI, queues everything else ---

    private class MessageListener implements Runnable {
        @Override
        public void run() {
            try {
                while (!socket.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof Message && ((Message) obj).getStatus().equals(Status.delivered)) {
                        final Message msg = (Message) obj;
                        Consumer<Message> cb = messageCallback;
                        if (cb != null) {
                            SwingUtilities.invokeLater(() -> cb.accept(msg));
                        }
                    } else {
                        serverMessages.put(obj);
                    }
                }
            } catch (Exception e) {
                // Socket closed or connection dropped — thread exits cleanly
            }
        }
    }
}
