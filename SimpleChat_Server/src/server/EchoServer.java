package server;

import java.io.*;
import java.util.*;
import ocsf.server.*;
import common.*;


public class EchoServer implements Observer {

    final public static int DEFAULT_PORT = 5555;

    static final String PASSWORDFILE = "./passwords.txt";   //나중에 경로 수정 필요할듯

    static final int LINEBREAK = 10;
    static final int RETURN = 13;
    static final int SPACE = 32;

    ObservableOriginatorServer server;

    String serverChannel = null;    

    Vector blockedUsers = new Vector();

    private ChatIF serverUI;

    private boolean closing = false;    

    public EchoServer(ObservableOriginatorServer server, ChatIF serverUI) throws IOException {   
        this.server = server;
        this.serverUI = serverUI;
        server.addObserver(this);   //this가 서버를 관찰할 수 있게
        server.listen();   
    }
    
    public void sendToAllClients(Object msg) {  
        Thread[] clients = server.getClientConnections();  

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c = (ConnectionToClient)(clients[i]);    

            try {
                if (((Boolean)(c.getInfo("passwordVerified"))).booleanValue())       
                    c.sendToClient(msg);
            } catch (IOException e) {
                serverUI.display("WARNING - Cannot send message to a client.");
            }
        }
    }

    public void update(Observable obs, Object msg) {
        if (! (msg instanceof OriginatorMessage))   //메시지 발신자
            return;
        
        OriginatorMessage message = (OriginatorMessage)msg;
        
        if (! (message.getMessage() instanceof String))
            return;
        
        String command = (String)message.getMessage();
        ConnectionToClient client = (ConnectionToClient)message.getOriginator();

        if (command.startsWith(ObservableServer.CLIENT_CONNECTED)) {
            clientConnected(client);
            return;
        } else if (command.startsWith(ObservableServer.CLIENT_DISCONNECTED)) {
            clientDisconnected(client);
            return;
        } else if (command.startsWith(ObservableServer.CLIENT_EXCEPTION)) {
            int ie = command.indexOf('.');
            clientException(client, new Exception(command.substring(ie)));
            return;
        } else if (command.startsWith(ObservableServer.LISTENING_EXCEPTION)) {
            int ie = command.indexOf('.');
            listeningException(new Exception(command.substring(ie)));
            return;
        } else if (command.startsWith(ObservableServer.SERVER_STARTED)) {
            serverStarted();
            return;
        } else if (command.startsWith(ObservableServer.SERVER_STOPPED)) {
            serverStopped();
            return;
        } else if (command.startsWith(ObservableServer.SERVER_CLOSED)) {
            serverClosed();
            return;
        }

        if (!blockedUsers.contains(((String)(client.getInfo("loginID"))))) {
            if (serverChannel == null || serverChannel.equals(client.getInfo("channel"))) {
                serverUI.display("Message: \"" + command + "\" from " + client.getInfo("loginID"));
            }
        }

        if (((Boolean)(client.getInfo("passwordVerified"))).booleanValue()) {   
            if (command.startsWith("#whoison")) 
                sendListOfClients(client);

            if (command.startsWith("#getchannel")) {    
                try {
                    client.sendToClient("Currently on channel: " + client.getInfo("channel"));
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
            }

            if (command.startsWith("#private")) 
                handleCmdPrivate(command, client);

            if (command.startsWith("#channel")) 
                handleCmdChannel(command, client);

            if (command.startsWith("#nochannel"))   
                handleCmdChannel("#channel main", client);

            if (command.startsWith("#pub")) 
                handleCmdPub(command, client);

            if (command.startsWith("#fwd")) 
                handleCmdFwd(command, client);

            if (command.startsWith("#unfwd")) { 
                client.setInfo("fwdClient", "");

                try {
                    client.sendToClient("Messages will no longer be forwarded");
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
            }

            if (command.startsWith("#block"))   
                handleCmdBlock(command, client);

            if (command.startsWith("#unblock")) 
                handleCmdUnblock(command, client);
                
            if (command.startsWith("#whoiblock"))   
                handleCmdWhoiblock(client);

            if (command.startsWith("#whoblocksme")) 
                checkForBlocks((String)(client.getInfo("loginID")), client);

            if (!command.startsWith("#")) { 
                sendChannelMessage(client.getInfo("loginID") + "> " + command, 
                    (String)client.getInfo("channel"), 
                    (String)(client.getInfo("loginID")));
            }
            return;
        }
        clientLoggingIn(command, client);   
    }

    public synchronized void handleMessageFromServerUI(String message) { 
        if (message.startsWith("#quit"))    
            quit();

        if (message.startsWith("#stop")) {  
            if (server.isListening()) {    
                server.stopListening();
                return;
            }  
            serverUI.display("Cannot stop the server before it is restarted.");
            return;
        }

        if (message.startsWith("#start")) { 
            closing = false;    
            if (!server.isListening()) {   
                try {
                    server.listen();   
                    serverChannel = null;   
                    return;
                } catch (IOException e) {   
                    serverUI.display("Cannot listen.  Terminating server.");
                    quit();
                }
            }
            serverUI.display("Server is already running.");
            return;
        }

        if (message.startsWith("#close")) { 
            closing = true; 
            sendToAllClients("Server is shutting down.");
            sendToAllClients("You will be disconnected.");

            try {   
                server.close();
            } catch (IOException e) {   
                serverUI.display("Cannot close normally.  Terminating server.");
                quit();
            }
            return;
        }

        if (message.startsWith("#getport")) {   
            serverUI.display("Current port : " + server.getPort());
            return;
        }
        
        if (message.startsWith("#setport")) {   
            if ((server.getNumberOfClients() != 0) || (server.isListening())) {   
                serverUI.display("Cannot change port while clients are "
                    + "connected or while server is listening");
                return;
            }
            try {
                int port = 0;
                port = Integer.parseInt(message.substring(9));  

                if ((port < 1024) || (port > 65535)) {   
                    server.setPort(DEFAULT_PORT);  
                    serverUI.display("Invalid port number.  Port unchanged.");
                    return;
                }
                server.setPort(port);
                serverUI.display("Port set to " + port);
            } catch (Exception e) { 
                serverUI.display("Invalid use of the #setport command.");
                serverUI.display("Port unchanged.");
            }
            return;
        }

        if (message.startsWith("#whoison")) {   
            sendListOfClients(null);
            return;
        }

        if (message.startsWith("#punt")) {  
            handleServerCmdPunt(message);
            return;
        }

        if (message.startsWith("#warn")) {  
            handleServerCmdWarn(message);
            return;
        }

        if (message.startsWith("#channel")) {   
            String oldChannel = serverChannel;
            if (!(oldChannel == null)) {    
                sendChannelMessage("The server has left this channel.", serverChannel, "");
            }

            try {
                serverChannel = message.substring(9);
            } catch (StringIndexOutOfBoundsException e) {
                serverChannel = null;   
                serverUI.display("Server will now receive all messages.");
            }

            if (serverChannel != null) {    
                sendChannelMessage("The server has joined this channel.", serverChannel, "");
            }

            serverUI.display("Now on channel: " + serverChannel);   
            return;
        }

        if (message.startsWith("#nochannel")) { 
            if (serverChannel != null) {
                sendChannelMessage("The server has left this channel.", serverChannel, "");
            }

            serverChannel = null;   
            serverUI.display("Server will now receive all messages.");
            return;
        }

        if (message.startsWith("#pub")) {   
            handleCmdPub(message, null);
            return;
        }
        
        if (message.startsWith("#getchannel")) {    
            if (server.isListening() || server.getNumberOfClients() > 0) {
                serverUI.display("Currently on channel: " + serverChannel);
                return;
            }
            serverUI.display("Server has no active channels.");
            return;
        }

        if (message.startsWith("#block")) { 
            handleServerCmdBlock(message);
            return;
        }

        if (message.startsWith("#unblock")) {   
            handleCmdUnblock(message, null);
            return;
        }

        if (message.startsWith("#whoiblock")) { 
            handleCmdWhoiblock(null);
            return;
        }

        if (message.startsWith("#private")) {   
            handleCmdPrivate(message, null);
            return;
        }

        if (message.startsWith("#whoblocksme")) {   
            checkForBlocks("server", null);
            return;
        }

        if (message.startsWith("#?") || message.startsWith("#help")) {  
            serverUI.display("\nServer-side command list:"
            + "\n#block <loginID> -- Block all messages from the specified client."
            + "\n#channel <channel> -- Connects to the specified channel."
            + "\n#close -- Stops the server and disconnects all users."
            + "\n#getchannel -- Gets the channel the server is currently connected to."
            + "\n#getport -- Gets the port the server is listening on."
            + "\n#help OR #? -- Lists all commands and their use."
            + "\n#nochannel -- Returns the server to the super-channel."
            + "\n#private <loginID> <msg> -- Sends a private message to the specified client."
            + "\n#pub -- Sends a public message."
            + "\n#punt <loginID> -- Kicks client out of the chatroom."
            + "\n#quit -- Terminates the server and disconnects all clients."
            + "\n#setport <newport> -- Specify the port the server will listen on."
            + "\n#start -- Makes the server restart accepting connections."
            + "\n#stop -- Makes the server stop accepting new connections."
            + "\n#unblock -- Unblock messages from all blocked clients."
            + "\n#unblock <loginID> -- Unblock messages from the specific client."
            + "\n#warn <loginID> -- Sends a warning message to the specified client."
            + "\n#whoblocksme -- List clients who are blocking messages from the server."   
            + "\n#whoiblock -- List all clients that the server is blocking messages from."
            + "\n#whoison -- Gets a list of all users and channel they are connected to.");
            return; 
        }

        if (!(message.startsWith("#"))) {   
            serverUI.display("SERVER MESSAGE> " + message);
            sendChannelMessage("SERVER MESSAGE> " + message, (serverChannel == null? "main" : serverChannel), "server");
            return;
        }        
        serverUI.display("Invalid command.");
    }

    public void quit() {    
        try{
            closing = true; 
            sendToAllClients("Server is quitting.");    
            sendToAllClients("You will be disconnected");   
            server.close();    
        } catch (IOException e) { } 
        System.exit(0);
    }

    protected void serverStarted() {    
        if(server.getNumberOfClients() != 0)   
            sendToAllClients("Server has restarted accepting connections.");
        
        serverUI.display("Server listening for connections on port " + server.getPort());
    }

    protected void serverStopped() {    
        serverUI.display("Server has stopped listening for connections.");
        
        if (!closing)   
            sendToAllClients("WARNING - Server has stopped accepting clients.");
    }

    protected void serverClosed() { 
        serverUI.display("Server is closed.");
    }

    protected void listeningException(Throwable exception) {
        serverUI.display("An error has occured while listening.");
    }

    protected void clientConnected(ConnectionToClient client) { 
        serverUI.display("A new client is attempting to connect to the server.");
        client.setInfo("loginID", "");  
        client.setInfo("channel", "");  
        client.setInfo("passwordVerified", new Boolean(false));
        client.setInfo("creatingNewAccount", new Boolean(false));
        client.setInfo("fwdClient", "");
        client.setInfo("blockedUsers", new Vector());

        try {
            client.sendToClient("Enter your login ID:");    
        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ex) { }
        }
    }

    protected synchronized void clientDisconnected(ConnectionToClient client){   
        handleDisconnect(client);   
    }

    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {   
        handleDisconnect(client);
    }

    private void handleCmdWhoiblock(ConnectionToClient client) {    
        Vector blocked;

        blocked = (client != null) ? (Vector)(client.getInfo("blockedUsers")) : blockedUsers;   

        Iterator blockedIterator = blocked.iterator();  

        if (blockedIterator.hasNext()) {    
            sendToClientOrServer(client, "BLOCKED USERS:");

            while (blockedIterator.hasNext()) { 
                String blockedUser = (String)blockedIterator.next();
                sendToClientOrServer(client, "Message from " + blockedUser + " are blocked.");
            }
            return;
        }
        sendToClientOrServer(client, "No blocking is in effect.");
    }

    private void handleCmdUnblock(String command, ConnectionToClient client) {  
        Vector blocked = null;  
        boolean removedUser = false;    
        String userToUnblock = null;    

        blocked = (client != null) ? (Vector)(client.getInfo("blockedUsers")) : blockedUsers;

        if (blocked.size() == 0) {  
            sendToClientOrServer(client, "No blocking is in effect.");
            return;
        }

        try {   
            userToUnblock = command.substring(9);
        } catch (StringIndexOutOfBoundsException e) {   
            userToUnblock = "";
        }

        if (userToUnblock.toLowerCase().equals("server"))   
            userToUnblock = "server";

        Iterator blockedIterator = blocked.iterator();
        while (blockedIterator.hasNext()) { 
            String blockedUser = (String)blockedIterator.next();
            
            if (blockedUser.equals(userToUnblock) || userToUnblock.equals("")) {    
                blockedIterator.remove();
                removedUser = true;
                sendToClientOrServer(client, "Message from " + blockedUser + " will now be displayed.");
            }
        }

        if (!removedUser) { 
            sendToClientOrServer(client, "Message from " + userToUnblock + " were not blocked.");
        }
    }

    private void handleCmdBlock(String command, ConnectionToClient client) {    
        Vector addBlock = null;

        try {
            String userToBlock = command.substring(7);  

            if (userToBlock.toLowerCase().equals("server")) {   
                userToBlock = "server";
            }

            if (userToBlock.equals(client.getInfo("loginID"))) {    
                try {
                    client.sendToClient("Cannot block the sending of messages to yourself.");
                } catch (IOException ex) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            }
            
            if (isLoginUsed(userToBlock) || userToBlock.equals("server")) { 
                if (isLoginBeingUsed(userToBlock, false) && !userToBlock.equals("server")) {    
                    ConnectionToClient toBlock = getClient(userToBlock);
                    
                    if (((String)(toBlock.getInfo("fwdClient"))).equals(((String)(client.getInfo("loginID"))))) {   
                        toBlock.setInfo("fwdClient", "");   
                        try {   
                            toBlock.sendToClient("Forwarding to " + client.getInfo("loginID")
                                + " has been cancelled because " + client.getInfo("loginID") 
                                + " is now blocking messages from you.");
                            client.sendToClient("Forwarding from "
                            + toBlock.getInfo("loginID") + " to you has been terminated.");
                        } catch (IOException ioe) {
                            serverUI.display("Warning: Error sending message.");
                        }
                    }
                }
                ((Vector)(client.getInfo("blockedUsers"))).addElement(userToBlock);    

                try {   
                    client.sendToClient("Messages from " + userToBlock + " will be blocked.");
                } catch (IOException ex) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            }
            
            try {   
                client.sendToClient("User " + userToBlock + " does not exist.");
            } catch (IOException ioe) {
                serverUI.display("Warning: Error sending message.");
            }
        } catch (StringIndexOutOfBoundsException e) {   
            try {
                client.sendToClient("ERROR - usage #block <loginID>");
            } catch (IOException ex) {
                serverUI.display("Warning: Error sending message.");
            }
        }
    }

    private void handleCmdFwd(String command, ConnectionToClient client) {  
        try {
            String destineeName = command.substring(5); 
            
            try {
                if (destineeName.equals(client.getInfo("loginID"))) {   
                    client.sendToClient("ERROR - Can't forward to self");
                    return;
                }
                if (destineeName.toLowerCase().equals("server")) {   
                    client.sendToClient("ERROR - Can't forward to SERVER");
                    return;
                }
                if (getClient(destineeName) == null) {  
                    client.sendToClient("ERROR - Client does not exist");
                    return;
                }
            } catch (IOException e) {
                serverUI.display("Warning: Error sending message.");
            }

            String tempFwdClient = (String)(client.getInfo("fwdClient"));

            ConnectionToClient destinee = getClient(destineeName);  

            if((((Vector)(destinee.getInfo("blockedUsers"))).contains((String)(client.getInfo("loginID"))))) { 
                try {
                    client.sendToClient("Cannot forward to " + destineeName + " because " + destineeName + " is blocking messages from you.");
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            }
            client.setInfo("fwdClient", destineeName);  

            try {
                if (isValidFwdClient(client)) { 
                    client.sendToClient("Message will be forwarded to: " + client.getInfo("fwdClient"));
                    return;
                }
                client.setInfo("fwdClient", tempFwdClient);
                client.sendToClient("ERROR - Can't forward because a loop would result");
                return;
            } catch (IOException e) { 
                serverUI.display("Warning: Error sending message.");
            }
        } catch (StringIndexOutOfBoundsException e) {   
            try {
                client.sendToClient("ERROR - usage: #fwd <loginID>");
            } catch (IOException ex) {
                serverUI.display("Warning: Error sending message.");
            }
        }
    }

    private void handleCmdPub(String command, ConnectionToClient client) {  
        String sender = "";
        
        try {   
            sender = (String)(client.getInfo("loginID"));
        } catch (NullPointerException e) {
            sender = "server";
        }

        try {
            Thread[] clients = server.getClientConnections();  

            for (int i = 0; i< clients.length; i++) {   
                ConnectionToClient c = (ConnectionToClient)(clients[i]);
                
                if (!((Vector)(c.getInfo("blockedUsers"))).contains(sender) && ((Boolean)(c.getInfo("passwordVerified"))).booleanValue()) {
                    c.sendToClient("PUBLIC MESSAGE from " + sender + "> " + command.substring(5));
                }
            }

            if (!blockedUsers.contains(sender)) {   
                serverUI.display("PUBLIC MESSAGE from " + sender + "> " + command.substring(5));
            }
        } catch (IOException e) {
            serverUI.display("Warning: Error sending message.");
        }
    }

    private void handleCmdChannel(String command, ConnectionToClient client) {  
        String oldChannel = (String)client.getInfo("channel");  
        String newChannel = "main"; 

        if (command.length() > 9)   
            newChannel = command.substring(9);
        
        client.setInfo("channel", newChannel);  
        if (!oldChannel.equals("main")) {   
            sendChannelMessage(client.getInfo("loginID") + " has left channel: " + oldChannel, oldChannel, "");
        }

        if (!newChannel.equals("main")) {   
            sendChannelMessage(client.getInfo("loginID") + " has joined channel: " + newChannel, newChannel, "");
        }

        if (serverChannel == null || serverChannel.equals(client.getInfo("channel"))) { 
            serverUI.display(client.getInfo("loginID") + " has joined channel: " + newChannel);
        }
    }

    private void handleCmdPrivate(String command, ConnectionToClient client) {  
        try {   
            int firstSpace = command.indexOf(" ");
            int secondSpace = command.indexOf(" ", firstSpace + 1);

            String sender = "";
            String loginID = command.substring(firstSpace + 1, secondSpace);
            String message = command.substring(secondSpace + 1);

            try {
                sender = (String)(client.getInfo("loginID"));   
            } catch (NullPointerException e) {
                sender = "server";
            }

            if (loginID.toLowerCase().equals("server")) {   
                if (!blockedUsers.contains(sender)) {   
                    serverUI.display("PRIVATE MESSAGE from " + sender + "> " + message);
                    return;
                }
                try {
                    client.sendToClient("Cannot send message because " + loginID + " is blocking messages from you.");
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            }
            try {
                Thread[] clients = server.getClientConnections();

                for (int i = 0; i < clients.length; i++) {
                    ConnectionToClient c = (ConnectionToClient)(clients[i]);
                    
                    if (c.getInfo("loginID").equals(loginID)) { 
                        if (!(((Vector)(c.getInfo("blockedUsers"))).contains(sender))) {    
                            if (!c.getInfo("fwdClient").equals("")) {   
                                getFwdClient(c, sender).sendToClient("Forwarded> PRIVATE MESSAGE from " + sender 
                                    + " to " + c.getInfo("loginID") + "> " + message);
                            } else {    
                                c.sendToClient("PRIVATE MESSAGE from " + sender + "> " + message);
                            }
                            serverUI.display("Private message: \"" +message + "\" from " + sender + " to " + c.getInfo("loginID")); 
                            return;
                        }
                        sendToClientOrServer(client, "Cannot send message because " + loginID + " is blocking message from you.");
                        return;
                    }
                }
            } catch (IOException e) {
                serverUI.display("Warning: Error sending message.");
            }
        } catch (StringIndexOutOfBoundsException e) {   
            sendToClientOrServer(client, "ERROR - usage: #private <loginID> <msg>");
        }
    }
    
    private void checkForBlocks(String login, ConnectionToClient client) {
        String results = "User block check:";

        if (!login.equals("server")) {  
            if (blockedUsers.contains(login)) { 
                results += "\nThe server is blocking message from you.";
            }
        }

        Thread[] clients = server.getClientConnections();

        for (int i = 0; i < clients.length; i++) {    
            ConnectionToClient c = (ConnectionToClient)(clients[i]);

            Vector blocked = (Vector)(c.getInfo("blockedUsers"));
            if (blocked.contains(login)) {
                results += "\nUser " + c.getInfo("loginID") + " is blocking your messages.";
            }
        }
        if (results.equals("User block check:"))    
            results += "\nNo user is blocking messages from you.";

        sendToClientOrServer(client, results);  
    }

    private boolean isValidFwdClient(ConnectionToClient client) {   
        boolean clientFound = false;
        ConnectionToClient testClient = client; 

        Thread[] clients = server.getClientConnections();
        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            if (tempc.getInfo("loginID").equals(testClient.getInfo("fwdClient"))) {    
                clientFound = true;
                break;
            }
        }

        if (!clientFound)   
            return false;
        
        String theClients[] = new String[server.getNumberOfClients() + 1];
        int i = 0;

        while (testClient != null && testClient.getInfo("fwdClient") != "") {   
            theClients[i] = (String)(testClient.getInfo("loginID"));    

            for (int j = 0; j < i; j++){    
                if(theClients[j].equals(theClients[i]))
                    return false;
            }
            i++;

            testClient = getClient((String)testClient.getInfo("fwdClient"));    
        }

        return true;
    }

    private ConnectionToClient getClient(String loginID) {  
        Thread[] clients = server.getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c = (ConnectionToClient)(clients[i]);
            if (c.getInfo("loginID").equals(loginID))
                return c;
        }
        return null;
    }

    private void clientLoggingIn(String message, ConnectionToClient client) {   
        if (message.equals(""))
            return;
        
        if ((client.getInfo("loginID").equals("")) && (message.equals("guest"))) {
            client.setInfo("creatingNewAccount", new Boolean(true));    

            try {
                client.sendToClient("\n*** CREATING NEW ACCOUNT ***\nEnter new LoginID :"); 
            } catch (IOException e) {
                try {
                    client.close();
                } catch (IOException ex) {}
            }
            return;
        }
        
        if ((client.getInfo("loginID").equals("") && ((Boolean)(client.getInfo("creatingNewAccount"))).booleanValue())) {
            client.setInfo("loginID", message);
            try {
                client.sendToClient("Enter new password :");    
            } catch (IOException e) {
                try {
                    client.close();
                }catch (IOException ex) { }
            }
            return;
        }
        
        if ((!client.getInfo("loginID").equals("")) && (((Boolean)(client.getInfo("creatingNewAccount"))).booleanValue())) {
            if (!isLoginUsed((String)(client.getInfo("loginID")))) {    
                client.setInfo("passwordVerified", new Boolean(true));
                client.setInfo("creatingNewAccount", new Boolean(false));
                client.setInfo("channel", "main");

                addClientToRegistry((String)(client.getInfo("loginID")), message);
                serverUI.display(client.getInfo("loginID") + " has logged on.");
                sendToAllClients(client.getInfo("loginID") + " has logged on.");
            } else {    
                client.setInfo("loginID", "");
                client.setInfo("creatingNewAccount", new Boolean(false));
                try{
                    client.sendToClient("login already in use.  Enter login ID:");
                } catch (IOException e) {
                    try {
                        client.close();
                    } catch (IOException ex) { }
                }
            }
            return;
        }
        if (client.getInfo("loginID").equals("")) { 
            client.setInfo("loginID", message);
            try {
                client.sendToClient("Enter password:");
            } catch (IOException e) {
                try {
                    client.close();
                } catch (IOException ex) { }
            }
            return;
        } 
        if ((isValidPwd((String)(client.getInfo("loginID")), message, true)) 
        && (!isLoginBeingUsed((String)(client.getInfo("loginID")), true))) {
            client.setInfo("passwordVerified", new Boolean(true));  
            client.setInfo("channel", "main");
            serverUI.display(client.getInfo("loginID") + " has logged on.");
            sendToAllClients(client.getInfo("loginID") + " has logged on.");
            return;
        } 
        try {   
            if (isLoginBeingUsed((String)(client.getInfo("loginID")), true)) {
                client.setInfo("loginID", "");
                client.sendToClient("Login ID is already logged on.\nEnter LoginID:");
                return;
            }    
            client.setInfo("loginID", "");
            client.sendToClient("\nIncorrect login or password\nEnter LoginID:");
        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ex) { }
        }
    }

    private void addClientToRegistry(String clientLoginID, String clientPassword) { 
        try {
            FileInputStream inputFile = new FileInputStream(PASSWORDFILE);
            byte buff[] = new byte[inputFile.available()];  

            for (int i = 0; i < buff.length; i++) { 
                int character = inputFile.read();
                buff[i] = (byte)character;
            }
            inputFile.close();

            File fileToBeDeleted = new File(PASSWORDFILE);
            fileToBeDeleted.delete();  

            FileOutputStream outputFile = new FileOutputStream(PASSWORDFILE);

            for (int i = 0; i< buff.length; i++)    
                outputFile.write(buff[i]);

            for (int i = 0; i < clientLoginID.length(); i++)    
                outputFile.write(clientLoginID.charAt(i));
            
            outputFile.write(SPACE);    

            for (int i = 0; i < clientPassword.length(); i++)   
                outputFile.write(clientPassword.charAt(i));

            outputFile.write(RETURN);   
            outputFile.write(LINEBREAK);    

            outputFile.close();
        } catch (IOException e) {   
            serverUI.display("ERROR - Password File Not Found");
        }
    }

    private boolean isLoginUsed(String loginID) {   
        return isValidPwd(loginID, "", false);
    }

    private boolean isValidPwd(String loginID, String password, boolean verifyPassword) {   
        try {
            FileInputStream inputFile = new FileInputStream(PASSWORDFILE);
            boolean eoln = false;   
            boolean eof = false;    

            while (!eof) {  
                eoln = false;
                String str = "";
                while (!eoln) {
                    int character = inputFile.read();  

                    if (character == -1) {
                        eof = true;
                        break;
                    }
                    if (character == LINEBREAK) {   
                        eoln = true;

                        if ((str.substring(0, str.indexOf(" ")).equals(loginID)) 
                            && ((str.substring(str.indexOf(" ") + 1).equals(password)) || (!verifyPassword))){ 
                            return true;
                        }
                        continue;
                    }
                    if (character != RETURN) {
                        str = str + (char)character;
                    }
                }
            }
            inputFile.close();
        } catch (IOException e) {
            serverUI.display("ERROR - Password File Not Found");
        }
        return false;
    }

    private boolean isLoginBeingUsed(String loginID, boolean checkForDup) { 
        boolean used = !checkForDup;    

        if (loginID.toLowerCase().equals("server")) 
            return true;

        Thread[] clients = server.getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            if (tempc.getInfo("loginID").equals(loginID)) {
                if (used)   
                    return true;
                used = true;
            }
        }
        return false;
    }

    private void sendChannelMessage(String message, String channel, String login) { 
        Thread[] clients = server.getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c = (ConnectionToClient)(clients[i]);

            if (c.getInfo("channel").equals(channel) && !(((Vector)(c.getInfo("blockedUsers"))).contains(login))) { 
                try {
                    if (!(c.getInfo("fwdClient").equals(""))) { 
                        getFwdClient(c, login).sendToClient("Forwarded> " + message);
                        continue;
                    }
                    c.sendToClient(message);
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
            }
        }
    }

    private ConnectionToClient getFwdClient(ConnectionToClient c, String sender) {  
        Vector pastRecipients = new Vector();

        pastRecipients.addElement(((String)(c.getInfo("loginID"))));    

        while (!c.getInfo("fwdClient").equals("")) {    
            Thread[] clients = server.getClientConnections();

            for (int i = 0; i < clients.length; i++) {
                ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            
                if (tempc.getInfo("loginID").equals(c.getInfo("fwdClient"))) {  
                    if (!(((Vector)(tempc.getInfo("blockedUsers"))).contains(sender))) {    
                        Iterator pastIterator = pastRecipients.iterator();

                        while (pastIterator.hasNext()) {
                            String pastRecipient = (String)pastIterator.next();
                            if (((Vector)(tempc.getInfo("blockedUsers"))).contains(pastRecipient)) {    
                                try {   
                                    c.sendToClient("Cannot forward message.  A past recipient of this message is blocked by "
                                        + (String)(tempc.getInfo("loginID")));
                                } catch (IOException e) {
                                    serverUI.display("Warning: Error sending message.");
                                }
                                return c;
                            }
                        }
                        if (!tempc.getInfo("fwdClient").equals("")) {   
                            c = tempc;  
                            pastRecipients.addElement(((String)(c.getInfo("loginID"))));    
                            break;
                        }
                        return tempc;   
                    }
                    try {
                        c.sendToClient("Cannot forward message.  Original sender is blocked by "
                        + ((String)(c.getInfo("fwdClient"))));
                    } catch (IOException e) {
                        serverUI.display("Warning: Error sending message.");
                    }
                    return c;   
                }
            }
        }
        return c;
    }

    private void sendListOfClients(ConnectionToClient c) {  
        Vector clientInfo = new Vector();
        Thread[] clients = server.getClientConnections();
        for (int i = 0; i < clients.length; i++) {  
            ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            clientInfo.addElement((String)(tempc.getInfo("loginID"))
            + " --- on channel: " + (String)(tempc.getInfo("channel")));
        }

        Collections.sort(clientInfo);   

        if (server.isListening() || server.getNumberOfClients() != 0) {   
            sendToClientOrServer(c, "SERVER --- on channel: "   
                + (serverChannel == null ? "main" : serverChannel));
        } else {    
            serverUI.display("SERVER --- no active channels");
        }

        Iterator toReturn = clientInfo.iterator();

        while (toReturn.hasNext()) {    
            sendToClientOrServer(c, (String)toReturn.next());
        }
    }

    private void handleServerCmdBlock(String message) { 
        try {
            String userToBlock = message.substring(7);

            if (userToBlock.toLowerCase().equals("server")) {   
                serverUI.display("Cannot block the sending of messages to yourself.");
                return;
            }
            if (!isLoginUsed(userToBlock)) {
                serverUI.display("User " + userToBlock + " does not exist.");
                return;
            }
            blockedUsers.addElement(userToBlock);
            serverUI.display("Messages from " + userToBlock + " will be blocked."); 
        } catch (StringIndexOutOfBoundsException e) {
            serverUI.display("ERROR - usage #block <loginID>");
        }
    }

    private void handleServerCmdPunt(String message) {  
        Thread[] clients = server.getClientConnections();

        try {
            for (int i = 0; i < clients.length; i++) {
                ConnectionToClient c = (ConnectionToClient)(clients[i]);
                if (c.getInfo("loginID").equals(message.substring(6))) {
                    try {
                        c.sendToClient("You have been expelled from this server.");
                    } catch (IOException e) {}
                    finally {
                        try {
                            c.close();
                        } catch (IOException ex) {}
                    }
                }
            }
        } catch (StringIndexOutOfBoundsException ex) {
            serverUI.display("Invalid use of the #punt command");
        }
    }

    private void handleServerCmdWarn(String message) {  
        Thread[] clients = server.getClientConnections();

        try {
            for (int i = 0; i < clients.length; i++) {
                ConnectionToClient c = (ConnectionToClient)(clients[i]);

                if (c.getInfo("loginID").equals(message.substring(6))) {
                    try {
                        c.sendToClient("Continue and you WILL be expelled.");
                    } catch (IOException e) {
                        try {
                            c.close();
                        } catch (IOException ex) { }
                    }
                }
            }
        } catch (StringIndexOutOfBoundsException ex) {
            serverUI.display("Invalid use of the #warn command");
        }
    }

    private void sendToClientOrServer(ConnectionToClient client, String message) {  
        try {
            client.sendToClient(message);
        } catch (NullPointerException npe) {
            serverUI.display(message);
        } catch (IOException ex) {
            serverUI.display("Warning: Error sending message.");
        }
    }

    private void handleDisconnect(ConnectionToClient client) {  
        if (!client.getInfo("loginID").equals("")) {
            try {
                Thread[] clients = server.getClientConnections();

                for (int i = 0; i < clients.length; i++) {
                    ConnectionToClient c = (ConnectionToClient)(clients[i]);
                    if (client.getInfo("loginID").equals(c.getInfo("fwdClient"))) { 
                        c.setInfo("fwdClient", "");
                        c.sendToClient("Forwarding to " + client.getInfo("loginID") + " has disconnected");
                    }
                }
                sendToAllClients(((client.getInfo("loginID") == null) ? "" : client.getInfo("loginID")) + " has disconnected.");    
            } catch (IOException e) {
                serverUI.display("Warning: Error sending message.");
            }
            serverUI.display(client.getInfo("loginID") + " has disconnected."); 
        }
    }
}