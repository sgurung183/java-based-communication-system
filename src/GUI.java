import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;

public class GUI {

    // -------------------------------------------------------------------------
    // Login window (unchanged behaviour from original)
    // -------------------------------------------------------------------------

    public static void loginWindow(ObjectOutputStream out, ObjectInputStream in) {
        JFrame frame = new JFrame("Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 400);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridLayout(3, 1, 0, 1));
        frame.add(mainPanel);

        JPanel userIdPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 50));
        JLabel idLabel = new JLabel("Employee ID: ");
        idLabel.setPreferredSize(new Dimension(100, 30));
        JTextField idField = new JTextField(13);
        idField.setPreferredSize(new Dimension(200, 30));
        userIdPanel.add(idLabel);
        userIdPanel.add(idField);
        mainPanel.add(userIdPanel);

        JPanel userPassPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JLabel passLabel = new JLabel("Password: ");
        passLabel.setPreferredSize(new Dimension(100, 30));
        JPasswordField passField = new JPasswordField(13);
        passField.setPreferredSize(new Dimension(200, 30));
        userPassPanel.add(passLabel);
        userPassPanel.add(passField);
        mainPanel.add(userPassPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton loginButton = new JButton("Login");
        loginButton.setPreferredSize(new Dimension(100, 30));
        buttonPanel.add(loginButton);
        mainPanel.add(buttonPanel);

        loginButton.addActionListener(e -> {
            String username = idField.getText().trim();
            String password = new String(passField.getPassword());
            try {
                out.writeObject(new User(username, password));
                frame.dispose();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        frame.setVisible(true);
    }

    public static void responseMessage(String msg) {
        JOptionPane.showMessageDialog(null, msg);
    }

    // -------------------------------------------------------------------------
    // Main chat window
    // -------------------------------------------------------------------------

    public static void showMainWindow(User user, Client client) {
        new MainWindow(user, client).setVisible(true);
    }

    static class MainWindow extends JFrame {

        private final User user;
        private final Client client;

        private final DefaultListModel<String> convListModel = new DefaultListModel<>();
        private final JList<String> convJList = new JList<>(convListModel);
        private final JTextArea messageArea = new JTextArea();
        private final JTextField inputField = new JTextField();
        private final JLabel chatHeader = new JLabel(" Select a conversation");

        private ConversationList allConversations = new ConversationList();
        private Conversation currentConversation = null;
        private final Set<String> unreadKeys = new HashSet<>();
        private boolean viewingAllConversations = false;

        MainWindow(User user, Client client) {
            super("Communication System — " + user.getFullName());
            this.user = user;
            this.client = client;
            client.setMessageCallback(this::onMessageReceived);
            buildUI();
            loadConversations();
        }

        private void buildUI() {
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    client.logout();
                    dispose();
                    System.exit(0);
                }
            });
            setSize(880, 620);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());

            // ---- Top bar ----
            JPanel topBar = new JPanel(new BorderLayout());
            topBar.setBackground(new Color(44, 62, 80));
            topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            JLabel userLabel = new JLabel(user.getFullName() + "  [" + user.getRole() + "]");
            userLabel.setForeground(Color.WHITE);
            userLabel.setFont(userLabel.getFont().deriveFont(Font.BOLD, 14f));
            topBar.add(userLabel, BorderLayout.WEST);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            btnPanel.setOpaque(false);
            if (user.getRole().equals(Role.ITUser)) {
                JButton allBtn = new JButton("All Conversations");
                allBtn.addActionListener(e -> loadAllConversations());
                btnPanel.add(allBtn);
            }
            JButton onlineBtn = new JButton("Online Users");
            JButton logoutBtn = new JButton("Log Out");
            onlineBtn.addActionListener(e -> showOnlineUsers());
            logoutBtn.addActionListener(e -> {
                client.logout();
                dispose();
                System.exit(0);
            });
            btnPanel.add(onlineBtn);
            btnPanel.add(logoutBtn);
            topBar.add(btnPanel, BorderLayout.EAST);
            add(topBar, BorderLayout.NORTH);

            // ---- Left sidebar: conversation list ----
            JPanel sidePanel = new JPanel(new BorderLayout(0, 6));
            sidePanel.setPreferredSize(new Dimension(220, 0));
            sidePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 6, 8, 6)
            ));

            JLabel convsTitle = new JLabel("Conversations");
            convsTitle.setFont(convsTitle.getFont().deriveFont(Font.BOLD, 13f));
            sidePanel.add(convsTitle, BorderLayout.NORTH);

            convJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            convJList.setFont(new Font("SansSerif", Font.PLAIN, 13));
            convJList.setFixedCellHeight(38);
            convJList.setCellRenderer(new ConvCellRenderer());
            sidePanel.add(new JScrollPane(convJList), BorderLayout.CENTER);

            JButton newChatBtn = new JButton("+ New Chat");
            sidePanel.add(newChatBtn, BorderLayout.SOUTH);
            newChatBtn.addActionListener(e -> showNewChatDialog());
            add(sidePanel, BorderLayout.WEST);

            // ---- Right panel: header + messages + input ----
            chatHeader.setFont(chatHeader.getFont().deriveFont(Font.BOLD, 14f));
            chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));

            messageArea.setEditable(false);
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);
            messageArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
            messageArea.setMargin(new Insets(8, 10, 8, 10));

            JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
            inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            JButton sendBtn = new JButton("Send");
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendBtn, BorderLayout.EAST);

            JPanel rightPanel = new JPanel(new BorderLayout());
            rightPanel.add(chatHeader, BorderLayout.NORTH);
            rightPanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);
            rightPanel.add(inputPanel, BorderLayout.SOUTH);
            add(rightPanel, BorderLayout.CENTER);

            // ---- Wire up events ----
            convJList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && convJList.getSelectedIndex() >= 0) {
                    int idx = convJList.getSelectedIndex();
                    if (idx < allConversations.size()) {
                        currentConversation = allConversations.get(idx);
                        unreadKeys.remove(getConversationKey(currentConversation));
                        convJList.repaint();
                        displayConversation(currentConversation);
                    }
                }
            });

            sendBtn.addActionListener(e -> sendMessage());
            inputField.addActionListener(e -> sendMessage());
        }

        // ---- Network actions (always off the EDT via SwingWorker) ----

        private void loadConversations() {
            new SwingWorker<ConversationList, Void>() {
                @Override
                protected ConversationList doInBackground() throws Exception {
                    return client.requestConversations();
                }
                @Override
                protected void done() {
                    try {
                        allConversations = get();
                        viewingAllConversations = false;
                        refreshConversationList();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainWindow.this, "Could not load conversations.");
                    }
                }
            }.execute();
        }

        private void loadAllConversations() {
            new SwingWorker<ConversationList, Void>() {
                @Override
                protected ConversationList doInBackground() throws Exception {
                    return client.requestAllConversations();
                }
                @Override
                protected void done() {
                    try {
                        allConversations = get();
                        currentConversation = null;
                        viewingAllConversations = true;
                        refreshConversationList();
                        messageArea.setText("");
                        chatHeader.setText(" All Conversations");
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainWindow.this, "Could not load conversations.");
                    }
                }
            }.execute();
        }

        private void sendMessage() {
            String text = inputField.getText().trim();
            if (text.isEmpty()) return;
            if (currentConversation == null) {
                JOptionPane.showMessageDialog(this, "Select a conversation or start a new chat first.");
                return;
            }
            inputField.setText("");
            List<String> recipients = currentConversation.getRecipients(user);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.sendMessage(recipients, text);
                    return null;
                }
                @Override
                protected void done() {
                    try {
                        get();
                        appendLine("[You]: " + text);
                        loadConversations();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainWindow.this, "Failed to send message.");
                        inputField.setText(text);
                    }
                }
            }.execute();
        }

        private void showNewChatDialog() {
            new SwingWorker<List<User>, Void>() {
                @Override
                protected List<User> doInBackground() throws Exception {
                    return client.requestAllUsers();
                }
                @Override
                protected void done() {
                    try {
                        List<User> all = get();
                        if (all.isEmpty()) {
                            JOptionPane.showMessageDialog(MainWindow.this, "No other users found.");
                            return;
                        }
                        DefaultListModel<String> model = new DefaultListModel<>();
                        for (User u : all) model.addElement(u.getFullName());
                        JList<String> list = new JList<>(model);
                        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                        list.setFont(new Font("SansSerif", Font.PLAIN, 13));

                        JPanel panel = new JPanel(new BorderLayout(0, 6));
                        panel.add(new JLabel("Select people to chat with (Ctrl/Shift to select multiple):"), BorderLayout.NORTH);
                        JScrollPane scroll = new JScrollPane(list);
                        scroll.setPreferredSize(new Dimension(250, 200));
                        panel.add(scroll, BorderLayout.CENTER);

                        int result = JOptionPane.showConfirmDialog(MainWindow.this, panel, "New Chat", JOptionPane.OK_CANCEL_OPTION);
                        if (result == JOptionPane.OK_OPTION && !list.isSelectionEmpty()) {
                            openOrCreateConversation(list.getSelectedValuesList());
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainWindow.this, "Could not load users.");
                    }
                }
            }.execute();
        }

        private void showOnlineUsers() {
            new SwingWorker<List<User>, Void>() {
                @Override
                protected List<User> doInBackground() throws Exception {
                    return client.requestOnlineUsers();
                }
                @Override
                protected void done() {
                    try {
                        List<User> online = get();
                        if (online.isEmpty()) {
                            JOptionPane.showMessageDialog(MainWindow.this, "No other users online right now.");
                        } else {
                            StringBuilder sb = new StringBuilder("<html><b>Online now:</b><br>");
                            for (User u : online) {
                                sb.append("&nbsp;&nbsp;&#9679;&nbsp;").append(u.getFullName()).append("<br>");
                            }
                            sb.append("</html>");
                            JOptionPane.showMessageDialog(MainWindow.this, sb.toString(),
                                "Online Users", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(MainWindow.this, "Could not load online users.");
                    }
                }
            }.execute();
        }

        // ---- UI helpers ----

        private void refreshConversationList() {
            allConversations.getConversationList().sort(
                (a, b) -> b.getLastMessageTimestamp().compareTo(a.getLastMessageTimestamp())
            );
            convListModel.clear();
            for (int i = 0; i < allConversations.size(); i++) {
                String label = allConversations.get(i).getRecipientsString(user);
                if (label == null || label.isEmpty()) {
                    label = allConversations.get(i).getMembersString();
                }
                convListModel.addElement(label);
            }
        }

        private void displayConversation(Conversation conv) {
            String header = conv.getRecipientsString(user);
            if (header == null || header.isEmpty()) header = conv.getMembersString();
            chatHeader.setText(" " + header);
            messageArea.setText(conv.getMessagesString());
            scrollToBottom();
        }

        private void openOrCreateConversation(List<String> targetNames) {
            List<String> targetUpper = new ArrayList<>();
            for (String n : targetNames) targetUpper.add(n.trim().toUpperCase());
            targetUpper.sort(null);

            for (int i = 0; i < allConversations.size(); i++) {
                List<String> recips = new ArrayList<>(allConversations.get(i).getRecipients(user));
                for (int j = 0; j < recips.size(); j++) recips.set(j, recips.get(j).toUpperCase());
                recips.sort(null);
                if (recips.equals(targetUpper)) {
                    convJList.setSelectedIndex(i);
                    return;
                }
            }
            // Create a placeholder so the user can type and send
            List<String> members = new ArrayList<>(targetUpper);
            members.add(user.getFullName().toUpperCase());
            members.sort(null);
            currentConversation = new Conversation(-1, members, new ArrayList<>());
            String header = String.join(", ", targetNames);
            chatHeader.setText(" " + header);
            messageArea.setText("(Start your conversation with " + header + ")\n");
        }

        private void appendLine(String line) {
            messageArea.append(line + "\n");
            scrollToBottom();
        }

        private void scrollToBottom() {
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        }

        private String getConversationKey(Conversation conv) {
            List<String> members = new ArrayList<>(conv.getMembersList());
            members.sort(null);
            return String.join(",", members);
        }

        private class ConvCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String label = value != null ? value.toString() : "";
                boolean isOwn = false;
                boolean isUnread = false;

                if (index < allConversations.size()) {
                    Conversation conv = allConversations.get(index);
                    String key = getConversationKey(conv);
                    isUnread = unreadKeys.contains(key);
                    if (viewingAllConversations) {
                        List<String> members = conv.getMembersList();
                        isOwn = members != null && members.contains(user.getFullName().toUpperCase());
                        if (isOwn) label = label + "  [You]";
                    }
                }

                Component c = super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
                if (isUnread) {
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    if (!isSelected) c.setBackground(new Color(255, 248, 220));
                }
                if (viewingAllConversations && isOwn && !isSelected && !isUnread) {
                    c.setBackground(new Color(225, 240, 255));
                }
                return c;
            }
        }

        // ---- Real-time message callback (runs on EDT) ----

        void onMessageReceived(Message msg) {
            List<String> msgMembers = msg.getMembers();
            msgMembers.sort(null);
            String key = String.join(",", msgMembers);
            String msgString = "[" + msg.getTimestamp() + "] " + msg.getSender().getFullName() + ": " + msg.getContent();

            // Update in-memory conversation or create a new one
            boolean found = false;
            for (int i = 0; i < allConversations.size(); i++) {
                if (getConversationKey(allConversations.get(i)).equals(key)) {
                    allConversations.get(i).addMessageString(msgString);
                    found = true;
                    break;
                }
            }
            if (!found) {
                Conversation newConv = new Conversation(-1, new ArrayList<>(msgMembers), new ArrayList<>());
                newConv.addMessageString(msgString);
                allConversations.addConversation(newConv);
            }

            String currentKey = currentConversation != null ? getConversationKey(currentConversation) : null;
            if (key.equals(currentKey)) {
                appendLine("[" + msg.getSender().getFullName() + "]: " + msg.getContent());
            } else {
                unreadKeys.add(key);
            }
            refreshConversationList();
        }
    }
}
