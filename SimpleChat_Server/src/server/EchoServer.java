package server;

import java.io.*;
import java.util.*;
import ocsf.server.*;
import common.*;

public class EchoServer extends AbstractServer {

    final public static int DEFAULT_PORT = 5555;

    static final String PASSWORDFILE = "C:\\workspace\\vscode\\SimpleChat\\SimpleChat_Server\\src\\passwards.txt";   //경로 임의 설정함.

    static final int LINEBREAK = 10;
    static final int RETURN = 13;
    static final int SPACE = 32;

    ChatIF serverUI;

    String serverChannel = null;

    Vector blockedUsers = new Vector();

    private boolean closing = false;    //서버의 연결이 닫히고 있는 중인지에 대한 변수로 false, true의 변화는 있으나 아직 조건문에 사용되지는 않음

    public EchoServer(int port, ChatIF serverUI) throws IOException {   //콘솔 객체 생성 동시에 생성되며 서버 UI가 있음. 생성 즉시 클라이언트의 연결 요청을 받을 수 있는 리슨 상태가 됨(이전 코드와의 차이점)
        super(port);
        this.serverUI = serverUI;
        listen();   //listen()으로 인해 추가 스레드 생성됨. 클라이언트와의 연결시 추가적으로 스레드가 생성되나 해제시 해당 스레드가 사라짐
    }
    
    public void sendToAllClients(Object msg) {
        Thread[] clients = getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c = (ConnectionToClient)(clients[i]);

            try {
                if (((Boolean)(c.getInfo("passwordVerfied"))).booleanValue())       //불린 객체의 값을 불린 기본형 값으로
                    c.sendToClient(msg);
            } catch (IOException e) {
                serverUI.display("WARNING - Cannot send message to a client.");
            }
        }
    }

    public synchronized void handleMessageFromClient(Object msg, ConnectionToClient client) {    //ocsf의 메소드가 구현되는 건 같으나 단순히 모든 클라이언트에게 메시지를 반환하는 것만이 아닌 로그인 요청에 대한 메시지 처리
        String command = (String)msg;
        
        if (!blockedUsers.contains(((String)(client.getInfo("loginID"))))) {
            if (serverChannel == null || serverChannel.equals(client.getInfo("channel"))) {
                serverUI.display("Message : \"" +command +"\" from " + client.getInfo("loginID"));  //받은 메시지 정보 표시
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
                handleCmdPrivate("#channel main", client);

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
        } else {
            clientLoggingIn(command, client);
        }
    }

    public synchronized void handleMessageFromServerUI(String message) { //서버 자신이 입력한 메시지들에 대한 처리 메소드
        if (message.startsWith("#quit"))    //종료 명령어를 입력했을 경우
            quit();

        if (message.startsWith("#stop")) {  //정지 명령어를 입력했을 경우
            if (isListening()) {    //현재 서버가 리스닝 중이었다면 리스닝을 정지.
                stopListening();
            } else {    //아니라면 이미 정지되거나 종료되어 서버가 재시작된 후에야 유효한 명령어임을 서버에게 알리는 메시지 표시
                serverUI.display("cannot stop the server before it is restarted.");
            }
            return;
        }

        if (message.startsWith("#start")) { //서버 재시작 명령어인 경우
            closing = false;    //닫히고 있지 않다는 불린
            if (!isListening()) {   //서버가 리스닝 되고 있지 않을 때
                try {
                    listen();   //리스닝 메소드 실행
                    serverChannel = null;   //처음 채널 메인으로
                } catch (IOException e) {   //예외 발생의 경우 서버를 종료함
                    serverUI.display("Cannot listen. Terminating server.");
                    quit();
                }
            } else {    //이미 리스닝 되고 있을 경우 이에 대한 메시지를 서버에게 표시
                serverUI.display("Server is already running.");
            }
            return;
        }

        if (message.startsWith("#close")) { //서버 닫음 명령어인 경우
            sendToAllClients("Server shutting down.");
            sendToAllClients("You will be disconnected.");

            closing = true; //닫히고 있다는 불린

            try {   //모든 클라이언트들에게 이에 대한 메시지를 보내고 닫는 메소드 실행
                close();
            } catch (IOException e) {   //예외로 인해 닫히지 않는다면 이에 대한 메시지를 서버에게 표시하고 종료 시도
                serverUI.display("Cannot close normally.  Terminating server.");
                quit();
            }
            return;
        }

        if (message.startsWith("#getport")) {   //현재 서버의 포트 번호를 얻는 명령어인 경우
            serverUI.display("Current port : " + getPort());
            return;
        }
        
        if (message.startsWith("#setport")) {   //서버의 포트 번호를 변경하는 명령어인 경우
            if ((getNumberOfClients() != 0) || (isListening())) {   //연결된 클라이언트가 있거나 서버가 리스닝 중인 경우엔 변경하지 못하도록 막음(요구사항)
                serverUI.display("Cannot change port while clients are connected or while server is listening");
            } else {
                try {
                    int port = 0;
                    port = Integer.parseInt(message.substring(9));  //"#setport " 이후의 문자열

                    if((port < 1024) || (port > 65535)) {   //well-known이거나 정상 포트 범위가 아닌 경우 
                        serverUI.display("Invalid port number.  Port unchanged.");
                    } else {    //가능한 정상 포트인 경우
                        setPort(port);
                        serverUI.display("Port set to " + port);
                    }
                } catch (Exception e) { //문자 등 비정상적인 포트 번호를 입력했을 때
                    serverUI.display("Invalid use of the #setport command.");
                    serverUI.display("Port unchanged.");
                }
            }
            return;
        }

        if (message.startsWith("#whoison")) {
            sendListOfClients(null);
            return;
        }

        if (message.startsWith("#punt")) {  //추방
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
            if (isListening() || getNumberOfClients() > 0)
                serverUI.display("Currently on channel: " + serverChannel);
            else
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
            + "\n#warn <loginID> -- Sends a warning message to the specified clients."
            + "\n#whoblocksme -- List clients who are blocking messages from the server."
            + "\n#whoiblock -- List all clients that the server is blocking messages from."
            + "\n#whoison -- Gets a list of all users and the channel they are connected to.");
        }

        if (!(message.startsWith("#"))) {   //#으로 시작하지 않는 모든 입력은 서버가 하는 메시지 전송이 됨.
            serverUI.display("SERVER MESSAGE> " + message);
            sendChannelMessage("SERVER MESSAGE> " + message, (serverChannel == null? "main" : serverChannel), "server");
        } else {    //메시지 전송도 아니고 올바른 명렁어도 아닌 경우(클라이언트의 코드와 비슷한 if문 구조인듯)
            serverUI.display("Invalid command.");
        }
    }

    public void quit() {    //종료에 대한 메소드
        try{
            closing = true; //연결 해제 중임에 대한 불린값
            sendToAllClients("Server is quitting.");    //서버가 종료될 것임을 모든 클라이언트들에게 알림
            sendToAllClients("You will be disconnected");   //서버와의 연결들이 해제될 것임을 모든 클라이언트들에게 알림
            close();    //ocsf의 메소드를 이용해 서버 닫음
        } catch (IOException e) { } //위의 사항이 이뤄지지 않더라도 서버 프로그램 종료
        System.exit(0);
    }

    protected void serverStarted() {    //서버가 시작되었을 경우에 대한 메시지 표시를 위해 사용되는 메소드
        if(getNumberOfClients() != 0)   //서버가 종료될 때 연결이 모두 해제되나, 어떠한 이유로 예외가 발생하여 시스템 종료만이 된 상황에 사용될 것으로 추측됨.
            sendToAllClients("Server has restarted accepting connections.");
        
        serverUI.display("Server listening for connections on port " + getPort());
    }

    protected void serverStopped() {    //서버가 정지될 때 서버와 연결된 클라이언트들에게 표시되는 메시지 메소드
        serverUI.display("Server has stopped listening for connections.");
        
        if (!closing)
            sendToAllClients("WARNING - Server has stopped accepting clients.");
    }

    protected void serverClosed() { //서버가 닫힐 때 서버에게 표시되는 메시지 메소드
        serverUI.display("Server is closed");
    }

    protected void clientConnected(ConnectionToClient client) { //새로운 클라이언트가 서버와의 연결을 시도할 때 서버에게 표시되는 메시지 메소드
        serverUI.display("A new client is attempting to connect " + "to the server.");
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
//////////////////////////////////////////////
    protected void clientDisconnected(ConnectionToClient client){   //서버와의 연결이 끊겼을 때 서버 및 다른 연결되어 있는 클라이언트들에게 보내는 메시지 메소드 
        handleDisconnect(client);
    }

    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {   //예외가 발생된 클라이언트에게 위와 같이 해당 메시지를 보내는 메소드로 보이나 실제 동작 확인 어려움 
        handleDisconnect(client);
    }

    private void handleCmdWhoiblock(ConnectionToClient client) {
        Vector blocked;

        if (client != null) {
            blocked = new Vector((Vector)(client.getInfo("blockedUsers")));
        } else {
            blocked = new Vector(blockedUsers);
        }

        Iterator blockedIterator = blocked.iterator();

        if (blockedIterator.hasNext()) {
            sendToClientOrServer(client, "BLOCKED USERS:");

            while (blockedIterator.hasNext()) {
                String blockedUser = (String)blockedIterator.next();
                sendToClientOrServer(client, "Message from " + blockedUser + " are blocked.");
            }
        } else {
            sendToClientOrServer(client, "No blocking is in effect.");
        }
    }

    private void handleCmdUnblock(String command, ConnectionToClient client) {
        Vector blocked = null;
        boolean removedUser = false;
        String userToUnblock = null;

        if (client != null) {
            blocked = new Vector((Vector)(client.getInfo("blockedUsers")));
        } else {
            blocked = new Vector(blockedUsers);
        }

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
                    client.sendToClient("Cannot block the sending of mesages to yourself.");
                } catch (IOException ex) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            } else {
                if (isLoginUsed(userToBlock) || userToBlock.equals("server")) {
                    if (isLoginBeingUsed(userToBlock, false) && !userToBlock.equals("server")) {
                        ConnectionToClient toBlock = getClient(userToBlock);
                        
                        if (((String)(toBlock.getInfo("fwdClient"))).equals(((String)(client.getInfo("loginID"))))) {
                            toBlock.setInfo("fwdClient", "");

                            try {
                                toBlock.sendToClient("Forwarding to "
                                    + client.getInfo("loginID")
                                    + " has been cancelled because "
                                    + client.getInfo("loginID") + " is now blocking messages from you.");
                                client.sendToClient("Forwarding from "
                                + client.getInfo("loginID") + " to you has been terminated.");
                            } catch (IOException ioe) {
                                serverUI.display("Warning: Error sending message.");
                            }
                        }
                    }
                    addBlock = (Vector)(client.getInfo("blockedUsers"));
                    addBlock.addElement(userToBlock);
                } else {
                    try {
                        client.sendToClient("User " + userToBlock + " does not exist.");
                    } catch (IOException ioe) {
                        serverUI.display("Warning: Error sending message.");
                    }
                    return;
                }

                try {
                    client.sendToClient("Messages from " + userToBlock + " will be bocked.");
                } catch (IOException ex) {
                    serverUI.display("Warning: Error sending message.");
                }
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
                } else {
                    if (destineeName.toLowerCase().equals("server")) {
                        client.sendToClient("ERROR - Can't forward to SERVER");
                        return;
                    } else {
                        if (getClient(destineeName) == null) {
                            client.sendToClient("ERROR - Client does not exist");
                            return;
                        }
                    }
                }
            } catch (IOException e) {
                serverUI.display("Warning: Error sending message.");
            }

            String tempFwdClient = (String)(client.getInfo("fwdClient"));
            ConnectionToClient destinee = getClient(destineeName);

            if(!(((Vector)(destinee.getInfo("blockedUsers"))).contains((String)(client.getInfo("loginID"))))) {
                client.setInfo("fwdClient", destineeName);
            } else {
                try {
                    client.sendToClient("Cannot forward to " + destineeName + " because" + destineeName + " is blocking messages from you.");
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            }

            try {
                if (isValidFwdClient(client)) {
                    client.sendToClient("Message will be forwarded to: " + client.getInfo("fwdClient"));
                } else {
                    client.setInfo("fwdClient", tempFwdClient);
                    client.sendToClient("ERROR - Can't forward because a loop would result");
                }
            } catch (IOException e) { 
                serverUI.display("Warning: Error sending message.");
            }
        } catch (StringIndexOutOfBoundsException e) {
            try {
                client.sendToClient("ERROR - usage: #fwd <loginID>");
            } catch (IOException ex) {
                serverUI.display("Warning: Error sendgin message.");
            }
        }
    }
    
    private void handleCmdPub(String command, ConnectionToClient client) {
        String sender = "";

        try {
            sender = (String)(client.getInfo("logindID"));
        } catch (NullPointerException e) {
            sender = "server";
        }

        try {
            Thread[] clients = getClientConnections();

            for (int i = 0; i< clients.length; i++) {
                ConnectionToClient c = (ConnectionToClient)(clients[i]);

                if (!((Vector)(c.getInfo("blockedUsers"))).contains(sender) && ((Boolean)(c.getInfo("passwordVerified"))).booleanValue()) {
                    c.sendToClient("PUBLIC MESSAGE from " + sender + "> " + command.substring(5));
                }
            }

            if (!blockedUsers.contains(sender)) {
                serverUI.display("PPUBLIC MESSAGE from " + sender + "> " + command.substring(5));
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

        if (!newChannel.equals("main")) {
            sendChannelMessage(client.getInfo("loginID") + " has left channel: " + oldChannel, oldChannel, "");
        }

        if (!newChannel.equals("main")) {
            sendChannelMessage(client.getInfo("loginID") + " has joined channel: " + newChannel, newChannel, "");
        }

        if (serverChannel == null || serverChannel.equals(client.getInfo("channel"))) {
            serverUI.display(client.getInfo("loginID") + "has joined cahnnel: "+newChannel);
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
                } else {
                    try {
                        client.sendToClient("Cannot send message because" + loginID + " is blocking messages from you.");
                    } catch (IOException e) {
                        serverUI.display("Warning: Error sending message.");
                    }
                }
            } else {
                try {
                    Thread[]clients = getClientConnections();

                    for (int i = 0; i < clients.length; i++) {
                        ConnectionToClient c = (ConnectionToClient)(clients[i]);
                        
                        if (c.getInfo("loginID").equals(loginID)) {
                            if (!(((Vector)(c.getInfo("blockedUsers"))).contains(sender))) {
                                if (!c.getInfo("fwdClient").equals("")) {
                                    getFwdClient(c, sender).sendToClient("Forwarded>PRIVATE MESSAGE from" + sender 
                                        + " to " + c.getInfo("loginID") + "> " + message);
                                } else {
                                    c.sendToClient("PRIVATE MESSAGE from " + sender + "> " + message);
                                }
                                serverUI.display("Private message: \"" +message + "\" from " + sender + " to " + c.getInfo("loginID"));
                            } else {
                                sendToClientOrServer(client, "Cannot send message because " + loginID + " is blocking message form you.");
                            }
                        }
                    }
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
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

        Thread[] clients = getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c= (ConnectionToClient)(clients[i]);

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

        Thread[] clients = getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            if (tempc.getInfo("loginID").equals(testClient.getInfo("fwdClient"))) {
                clientFound = true;
            }
        }

        if (!clientFound)
            return false;
        
            String theClients[] = new String[getNumberOfClients() + 1];
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
        Thread[] clients = getClientConnections();

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
        //게스트로 로그인 할 경우
        if ((client.getInfo("loginID").equals("")) && (message.equals("guest"))) {
            client.setInfo("creatngNewAccount", new Boolean(true));

            try {
                client.sendToClient("\n*** CREATING NEW ACCOUNT ***\nEnter new LoginID :");
            } catch (IOException e) {
                try {
                    client.close();
                } catch (IOException ex) { }
            }
        //로그인 아이디가 있거나 게스트가 아닌 로그인을 할 경우
        } else {
            //로그인 아이디가 없고 계정 생성을 하는 중인 경우
            if ((client.getInfo("loginID").equals("") && ((Boolean)(client.getInfo("creatingNewAccount"))).booleanValue())) {
                client.setInfo("loginID", message); //메세지=로그인아이디

                try {
                    client.sendToClient("Enter new password :");
                } catch (IOException e) {
                    try {
                        client.close();
                    }catch (IOException ex) { }
                }
            //로그인 아이디가 있거나 계정생성을 하지 않는 경우
            } else {
                //로그인 아이디가 있고 계정 생성을 하는 중인 경우
                if ((!client.getInfo("loginID").equals("")) && (((Boolean)(client.getInfo("creatingNewAccount"))).booleanValue())) {
                    //로그인에 사용된 적이 없는 아이디일 경우
                    if (!isLoginUsed((String)(client.getInfo("loginID")))) {
                        client.setInfo("passwordVerified", new Boolean(true));
                        client.setInfo("creatingNewAccount", new Boolean(false));
                        client.setInfo("channel", "main");
                        addClientToRegistry((String)(client.getInfo("loginID")), message);
                        serverUI.display(client.getInfo("loginID") + " has logged on.");
                        sendToAllClients(client.getInfo("loginID") + " has logged on.");
                    //로그인에 사용된 적이 있는 아이디였을 경우
                    } else {
                        client.setInfo("loginID", "");
                        client.setInfo("creatingNewAccount", new Boolean(false));

                        try{
                            client.sendToClient("login already in use.   Enter login ID:");
                        } catch (IOException e) {
                            try {
                                client.close();
                            } catch (IOException ex) { }
                        }
                    }
                //로그인 아이디가 없거나 계정 생성을 하지 않는 경우
                } else {
                    //로그인 아이디가 없는 경우
                    if (client.getInfo("loginID").equals("")) {
                        client.setInfo("loginID", message);

                        try {
                            client.sendToClient("Enter password:");
                        } catch (IOException e) {
                            try {
                                client.close();
                            } catch (IOException ex) { }
                        }
                    //로그인 아이디가 있는 경우
                    } else {
                        if ((isValidPwd((String)(client.getInfo("loginID")), message, true)) 
                        && (!isLoginBeingUsed((String)(client.getInfo("loginID")), true))) {
                                client.setInfo("passwordVerified", new Boolean(true));
                                client.setInfo("channel", "main");
                                serverUI.display(client.getInfo("loginID") + " has logged on.");
                                sendToAllClients(client.getInfo("loginID") + " has logged on.");
                        } else {
                            try {
                                if (isLoginBeingUsed((String)(client.getInfo("loginID")), true)) {
                                    client.setInfo("loginID", "");
                                    client.sendToClient("Login ID is already logged on.\nEnter LoginID:");
                                } else {
                                    client.setInfo("loginID", "");
                                    client.sendToClient("\nIncorrect login or password\nEnter LoginID:");
                                }
                            } catch (IOException e) {
                                try {
                                    client.close();
                                } catch (IOException ex) { }
                            }
                        }
                    }
                }
            }
        } 
    }

    private void addClientToRegistry(String clientLoginID, String clientPassword) {
        try {
            FileInputStream inputFile = new FileInputStream(PASSWORDFILE);
            byte buff[] = new byte[inputFile.available()];  //현재 읽기 가능한 바이트 수

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
                    } else {
                        if (character == LINEBREAK) {
                            eoln = true;

                            if ((str.substring(0, str.indexOf(" ")).equals(loginID)) 
                             && ((str.substring(str.indexOf(" ") + 1).equals(password)) || (!verifyPassword))){
                                return true;
                            }
                        } else {
                            if (character != RETURN) {
                                str = str + (char)character;
                            }
                        }
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

        Thread[] clients = getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            if (tempc.getInfo("loginID").equals(loginID)) {
                if (used)   //지금 계정 생성 중일 때(하나만 있음)
                    return true;
                used = true;    //안에서 한 번 거치고 2번째가 있을 때
            }
        }
        return false;
    }

    private void sendChannelMessage(String message, String channel, String login) {
        Thread[] clients = getClientConnections();

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c = (ConnectionToClient)(clients[i]);

            if (c.getInfo("channel").equals(channel) && !(((Vector)(c.getInfo("blockedUsers"))).contains(login))) {
                try {
                    if (!(c.getInfo("fwdClient").equals(""))) {
                        getFwdClient(c, login).sendToClient("Forwarded> " + message);
                    } else {
                        c.sendToClient(message);
                    }
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
            Thread[] clients = getClientConnections();

            for (int i = 0; i < clients.length; i++) {
                ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            
                if (tempc.getInfo("loginID").equals(c.getInfo("fwdClient"))) {
                    if (!(((Vector)(tempc.getInfo("blockedUsers"))).contains(sender))) {
                        Iterator pastIterator = pastRecipients.iterator();

                        while (pastIterator.hasNext()) {
                            String pastRecipient = (String)pastIterator.next();
                            if (((Vector)(tempc.getInfo("blockedUsers"))).contains(pastRecipient)) {
                                try {
                                    c.sendToClient("Cannot forward message.  A past " 
                                    + "recipient of this message is blocked by "
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
                        } else {
                            return tempc;
                        }
                    } else {
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
        }
        return c;
    }

    private void sendListOfClients(ConnectionToClient c) {
        Vector clientInfo = new Vector();
        Thread[] clients = getClientConnections();
        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient tempc = (ConnectionToClient)(clients[i]);
            clientInfo.addElement((String)(tempc.getInfo("loginID"))
            + " --- on channel: " + (String)(tempc.getInfo("channel")));
        }

        Collections.sort(clientInfo);

        if (isListening() || getNumberOfClients() != 0) {
            sendToClientOrServer(c, "SERVER --- on channel: "
                + (serverChannel == null ? "main" : serverChannel));
        } else {
            serverUI.display("SERVER --- no active cahnnels");
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
                serverUI.display("Cannot block the sending of message to yourself.");
                return;
            } else {
                if (isLoginUsed(userToBlock)) {
                    blockedUsers.addElement(userToBlock);
                } else {
                    serverUI.display("User " + userToBlock + " does not exist.");
                    return;
                }
            }
            serverUI.display("Messages from " + userToBlock + " will be blocked.");
        } catch (StringIndexOutOfBoundsException e) {
            serverUI.display("ERROR - usage #block <loginID>");
        }
    }

    private void handleServerCmdPunt(String message) {
        Thread[] clients = getClientConnections();

        try {
            for (int i = 0; i < clients.length; i++) {
                ConnectionToClient c = (ConnectionToClient)(clients[i]);

                if (c.getInfo("loginID").equals(message.substring(6))) {
                    try {
                        c.sendToClient("You have been expelled from this server.");
                    } catch (IOException e) { }
                }
            }
        } catch (StringIndexOutOfBoundsException ex) {
            serverUI.display("Invalid use of the #punt command");
        }
    }

    private void handleServerCmdWarn(String message) {
        Thread[] clients = getClientConnections();

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
                Thread[] clients = getClientConnections();

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