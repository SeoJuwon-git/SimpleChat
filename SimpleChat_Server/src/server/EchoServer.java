package server;

import java.io.*;
import java.util.*;
import ocsf.server.*;
import common.*;

public class EchoServer extends AbstractServer {

    final public static int DEFAULT_PORT = 5555;

    static final String PASSWORDFILE = "C:\\workspace\\vscode\\SimpleChat\\SimpleChat_Server\\src\\passwords.txt";   //경로 임의 설정 및 미리 파일 생성함.

    static final int LINEBREAK = 10;
    static final int RETURN = 13;
    static final int SPACE = 32;

    ChatIF serverUI;

    String serverChannel = null;    //채널 기능이 추가됨에 따른 변수

    Vector blockedUsers = new Vector(); //블록한 유저들

    private boolean closing = false;    //서버의 연결이 닫히고 있는 중인지에 대한 변수로 false, true의 변화는 있으나 아직 조건문에 사용되지는 않음

    public EchoServer(int port, ChatIF serverUI) throws IOException {   //콘솔 객체 생성 동시에 생성되며 서버 UI가 있음. 생성 즉시 클라이언트의 연결 요청을 받을 수 있는 리슨 상태가 됨(이전 코드와의 차이점)
        super(port);
        this.serverUI = serverUI;
        listen();   //listen()으로 인해 추가 스레드 생성됨. 클라이언트와의 연결시 추가적으로 스레드가 생성되나 해제시 해당 스레드가 사라짐
    }
    
    public void sendToAllClients(Object msg) {  //모든 클라이언트들에게 보내는 ocsf의 메소드를 사용만 하던 전 코드와 달리 구현함.
        Thread[] clients = getClientConnections();  //해당 ocsf 메소드를 통해 클라이언트의 연결들을 가져옴.

        for (int i = 0; i < clients.length; i++) {
            ConnectionToClient c = (ConnectionToClient)(clients[i]);    //각 연결에 대해서

            try {
                if (((Boolean)(c.getInfo("passwordVerified"))).booleanValue())       //패스워드가 검증된 연결만 추려(불린 객체의 값을 불린 기본형 값으로 바꾸는 함수 사용) 메시지를 보냄.
                    c.sendToClient(msg);
            } catch (IOException e) {
                serverUI.display("WARNING - Cannot send message to a client.");
            }
        }
    }

    public synchronized void handleMessageFromClient(Object msg, ConnectionToClient client) {    //ocsf의 메소드가 구현되는 건 같으나 단순히 모든 클라이언트에게 메시지를 반환하는 것만이 아닌 로그인 요청에 대한 메시지 처리
        String command = (String)msg;
        
        if (!blockedUsers.contains(((String)(client.getInfo("loginID"))))) {    //서버가 블록한 클라이언트에게서 온 메시지가 아니라면
            if (serverChannel == null || serverChannel.equals(client.getInfo("channel"))) { //서버채널이 메인이거나 해당 클라이언트와 채널이 같을 경우에만
                serverUI.display("Message : \"" + command + "\" from " + client.getInfo("loginID"));  //받은 메시지 정보 표시
            }
        }

        if (((Boolean)(client.getInfo("passwordVerified"))).booleanValue()) {   //검증된 패스워드를 가진 클라이언트에 대해서만 해당 명령어들이 가능하도록 함.
            if (command.startsWith("#whoison")) //같은 채널 접속자 확인 메소드 호출
                sendListOfClients(client);

            if (command.startsWith("#getchannel")) {    //현재 채널명 표시
                try {
                    client.sendToClient("Currently on channel: " + client.getInfo("channel"));
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
            }

            if (command.startsWith("#private")) //개인적 메시지 메소드 호출
                handleCmdPrivate(command, client);

            if (command.startsWith("#channel")) //채널 변경 메소드 호출
                handleCmdChannel(command, client);

            if (command.startsWith("#nochannel"))   //메인 채널로 변경 메소드 호출(매개변수 차이)
                handleCmdChannel("#channel main", client);

            if (command.startsWith("#pub")) //공적 메시지 메소드 호출
                handleCmdPub(command, client);

            if (command.startsWith("#fwd")) //메시지 전달 메소드 호출(서버에선 사용하지 않음)
                handleCmdFwd(command, client);

            if (command.startsWith("#unfwd")) { //메시지 전달 중지 메소드 호출
                client.setInfo("fwdClient", "");

                try {
                    client.sendToClient("Messages will no longer be forwarded");
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
            }

            if (command.startsWith("#block"))   //블록 메소드 호출
                handleCmdBlock(command, client);

            if (command.startsWith("#unblock")) //블록 해제 메소드 호출
                handleCmdUnblock(command, client);
                
            if (command.startsWith("#whoiblock"))   //자신이 블록한 목록 메소드 호출
                handleCmdWhoiblock(client);

            if (command.startsWith("#whoblocksme")) //자신이 블록당한 목록 메소드 호출
                checkForBlocks((String)(client.getInfo("loginID")), client);

            if (!command.startsWith("#")) { //일반 메시지에 대해서 채널 메시지 메소드 호출
                sendChannelMessage(client.getInfo("loginID") + "> " + command, 
                    (String)client.getInfo("channel"), 
                    (String)(client.getInfo("loginID")));
            }
        } else {
            clientLoggingIn(command, client);   //아직 검증되지 않은 패스워드를 가진 클라이언트였다면 로그인 메소드 호출
        }
    }

    public synchronized void handleMessageFromServerUI(String message) { //서버 자신이 입력한 메시지들에 대한 처리 메소드
        if (message.startsWith("#quit"))    //종료 명령어를 입력했을 경우
            quit();

        if (message.startsWith("#stop")) {  //정지 명령어를 입력했을 경우
            if (isListening()) {    //현재 서버가 리스닝 중이었다면 리스닝을 정지.
                stopListening();
            } else {    //아니라면 이미 정지되거나 종료되어 서버가 재시작된 후에야 유효한 명령어임을 서버에게 알리는 메시지 표시
                serverUI.display("Cannot stop the server before it is restarted.");
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
                    serverUI.display("Cannot listen.  Terminating server.");
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
            if ((getNumberOfClients() != 0) || (isListening())) {   //연결된 클라이언트가 있거나 서버가 리스닝 중인 경우엔 변경하지 못하도록 막음(이전 코드의 요구사항)
                serverUI.display("Cannot change port while clients are connected or while server is listening");
            } else {
                try {
                    int port = 0;
                    port = Integer.parseInt(message.substring(9));  //"#setport " 이후의 문자열

                    if ((port < 1024) || (port > 65535)) {   //well-known이거나 정상 포트 범위가 아닌 경우 
                        setPort(5555);  //(DEFAULT_PORT가 아니라?) 일단 잘못된 포트 번호를 입력하더라도 기본 포트로 설정하는 것이 이전 코드와 달라진 점.
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

        if (message.startsWith("#whoison")) {   //클라이언트와 동일하게 메소드 호출(매개변수 차이)
            sendListOfClients(null);
            return;
        }

        if (message.startsWith("#punt")) {  //추방 메소드 호출
            handleServerCmdPunt(message);
            return;
        }

        if (message.startsWith("#warn")) {  //경고 메소드 호출
            handleServerCmdWarn(message);
            return;
        }

        if (message.startsWith("#channel")) {   //서버의 채널을 바꾸는 명령어인 경우
            String oldChannel = serverChannel;

            if (!(oldChannel == null)) {    //서버가(login="") 메인 채널에서 이동하는 게 아닌 경우 채널에 이 채널을 떠난다는 메시지를 보냄
                sendChannelMessage("The server has left this channel.", serverChannel, "");
            }

            try {
                serverChannel = message.substring(9);
            } catch (StringIndexOutOfBoundsException e) {
                serverChannel = null;   //해당 입력 예외가 발생했을 경우 메인 서버로
                serverUI.display("Server will now receive all messages.");
            }

            if (serverChannel != null) {    //메인 메시지로 가는게 아니라면 해당 채널에 접속해있는 유저들에게 해당 메시지를 보냄(저 메소드 상에서 당사자는 해당 메시지를 받지 않음)
                sendChannelMessage("The server has joined this channel.", serverChannel, "");
            }

            serverUI.display("Now on channel: " + serverChannel);   //변경된 채널명 표시
            return;
        }

        if (message.startsWith("#nochannel")) { //서버의 채널을 메인 채널로 바꾸는 명령어인 경우
            if (serverChannel != null) {
                sendChannelMessage("The server has left this channel.", serverChannel, "");
            }

            serverChannel = null;   //메인 채널로 변경
            serverUI.display("Server will now receive all messages.");
            return;
        }

        if (message.startsWith("#pub")) {   //서버가 공적인 메시지를 보낼 경우 해당 메소드 호출(클라이언트와 동일)
            handleCmdPub(message, null);
            return;
        }
        //리스닝 중이거나 연결된 클라이언트가 있는 경우 서버의 채널을 표시, 아니라면 활성화된 채널이 없다고 표시(서버 혼자 있을 경우 활성화하지 않은 것으로 생각하는듯.)
        if (message.startsWith("#getchannel")) {    
            if (isListening() || getNumberOfClients() > 0)
                serverUI.display("Currently on channel: " + serverChannel);
            else
                serverUI.display("Server has no active channels.");
            return;
        }

        if (message.startsWith("#block")) { //블록에 대한 메소드 호출(클라이언트의 메시지 처리와 다른 메소드)
            handleServerCmdBlock(message);
            return;
        }

        if (message.startsWith("#unblock")) {   //불록 해제에 대한 메소드 호출(클라이언트와 같은 메소드로 호출)
            handleCmdUnblock(message, null);
            return;
        }

        if (message.startsWith("#whoiblock")) { //서버가 블록한 목록 표시 메소드 호출(매개변수 차이)
            handleCmdWhoiblock(null);
            return;
        }

        if (message.startsWith("#private")) {   //사적인 메시지 메소드 호출(매개변수 차이)
            handleCmdPrivate(message, null);
            return;
        }

        if (message.startsWith("#whoblocksme")) {   //나를 블록한 목록 메소드 호출(매개변수 차이)
            checkForBlocks("server", null);
            return;
        }

        if (message.startsWith("#?") || message.startsWith("#help")) {  //서버가 가능한 명령어에 대한 도움말 목록 표시
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
            + "\n#whoblocksme -- List clients who are blocking messages from the server."   //책 오타?whoblockme->whoblocksme
            + "\n#whoiblock -- List all clients that the server is blocking messages from."
            + "\n#whoison -- Gets a list of all users and the channel they are connected to.");
            return; //교재는 없으나 이게 없으면 Invalid command 조건문으로 들어가는 문제 발생
        }

        if (!(message.startsWith("#"))) {   //#으로 시작하지 않는 모든 입력은 서버가 하는 메시지 전송이 됨. 다만 sendToAllClients였던 이전 코드와는 달리 채널 메세지 전송이 되는 차이점.
            serverUI.display("SERVER MESSAGE> " + message);
            sendChannelMessage("SERVER MESSAGE> " + message, (serverChannel == null? "main" : serverChannel), "server");
        } else {    //메시지 전송도 아니고 올바른 명렁어도 아닌 경우
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
        
        if (!closing)   //서버가 닫히는 중이라면 굳이 이 메시지를 표시하지 않도록 함.(이전 코드와 달리 closing 변수 활용)
            sendToAllClients("WARNING - Server has stopped accepting clients.");
    }

    protected void serverClosed() { //서버가 닫힐 때 서버에게 표시되는 메시지 메소드
        serverUI.display("Server is closed");
    }

    protected void clientConnected(ConnectionToClient client) { //새로운 클라이언트가 서버와의 연결을 시도할 때 서버에게 표시되는 메시지 메소드
        serverUI.display("A new client is attempting to connect to the server.");
        client.setInfo("loginID", "");  //클라이언트가 연결되었을 때 로그인 아이디, 채널, 패스워드검증, 새 계정생성중인지, 메시지 전달, 블록 목록 등을 모두 초기화함.
        client.setInfo("channel", "");  //따라서 연결시에 모든 정보가 초기화되며 남는 것은 passwords 및 이미 연결되어 있는 서버나 클라이언트의 정보들.
        client.setInfo("passwordVerified", new Boolean(false));
        client.setInfo("creatingNewAccount", new Boolean(false));
        client.setInfo("fwdClient", "");
        client.setInfo("blockedUsers", new Vector());

        try {
            client.sendToClient("Enter your login ID:");    //클라이언트에게 로그인 아이디를 입력하라는 해당 메시지를 날리며 이후 클라이언트의 입력은 패스워드검증이 false이므로 로그인 과정을 거치게 됨.
        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ex) { }
        }
    }

    protected synchronized void clientDisconnected(ConnectionToClient client){   //서버와의 연결이 끊겼을 때 관련 메소드 호출(이전 코드와는 synchronized가 추가된 차이도 있음.)
        handleDisconnect(client);   
    }

    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {   //예외가 발생된 클라이언트에게 해당 메소드 호출
        handleDisconnect(client);
    }

    private void handleCmdWhoiblock(ConnectionToClient client) {    //내가 블록한 목록을 표시하기 위한 메소드
        Vector blocked;

        if (client != null) {   //서버가 아니라면 해당 클라이언트의 정보에서 가져오게 되며
            blocked = new Vector((Vector)(client.getInfo("blockedUsers")));
        } else {    //서버라면 서버가 가진 블록 목록들을 가져옴.
            blocked = new Vector(blockedUsers);
        }

        Iterator blockedIterator = blocked.iterator();  //저장된 블록 목록 읽기 위한 이터레이터

        if (blockedIterator.hasNext()) {    //안에 요소가 있다면 아래 메시지부터 표시
            sendToClientOrServer(client, "BLOCKED USERS:");

            while (blockedIterator.hasNext()) { //요소를 순차적으로 탐색하며 클라이언트 혹은 서버에게 해당 메시지 전송
                String blockedUser = (String)blockedIterator.next();
                sendToClientOrServer(client, "Message from " + blockedUser + " are blocked.");
            }
        } else {    //안에 요소가 아예 없을 경우
            sendToClientOrServer(client, "No blocking is in effect.");
        }
    }

    private void handleCmdUnblock(String command, ConnectionToClient client) {  //클라이언트 혹은 서버의 블록 해제를 다루는 메소드
        Vector blocked = null;  //블록 목록
        boolean removedUser = false;    //블록 해제되는 유저가 하나라도 있는지 판별
        String userToUnblock = null;    //블록 해제되는 유저(있을 경우와 없을 경우로 나뉠 수 있음)

        if (client != null) {   //whoiblock과 new Vector로 감싸는 여부 차이가 있음.
            blocked = (Vector)(client.getInfo("blockedUsers"));
        } else {
            blocked = blockedUsers;
        }

        if (blocked.size() == 0) {  //블록을 해제할 요소가 없는 경우
            sendToClientOrServer(client, "No blocking is in effect.");
            return;
        }

        try {   //특정 유저만 해제할 경우
            userToUnblock = command.substring(9);
        } catch (StringIndexOutOfBoundsException e) {   //입력이 없어 모든 유저를 해제하는 경우 
            userToUnblock = "";
        }

        if (userToUnblock.toLowerCase().equals("server"))   //서버를 해제하는 경우 대소문자 구분없이 처리
            userToUnblock = "server";

        Iterator blockedIterator = blocked.iterator();

        while (blockedIterator.hasNext()) { //목록의 요소들을 탐색하며
            String blockedUser = (String)blockedIterator.next();

            if (blockedUser.equals(userToUnblock) || userToUnblock.equals("")) {    //삭제할 유저 혹은 모든 유저 삭제일 경우 next()로 나온 해당 유저를 블록해제(remove)함.
                blockedIterator.remove();
                removedUser = true;
                sendToClientOrServer(client, "Message from " + blockedUser + " will now be displayed.");
            }
        }

        if (!removedUser) { //if (blocked.size() == 0)로 아예 없었을 경우는 블록이 없다고 알리고 끝나게 되므로 이곳에선 특정 유저를 해제하는 unblock의 경우만 해당될 것.
            sendToClientOrServer(client, "Message from " + userToUnblock + " were not blocked.");
        }
    }

    private void handleCmdBlock(String command, ConnectionToClient client) {    //블록을 하는 경우의 메소드
        Vector addBlock = null;

        try {
            String userToBlock = command.substring(7);  //이슈1에 의하면 여러 유저를 한번에 블록하는 부분이 구현되어야 하는 것으로 생각했으나 그런 부분이 없는 것 같음.

            if (userToBlock.toLowerCase().equals("server")) {   //서버를 대상으로 블록하는 경우 대소문자 구분없이 처리
                userToBlock = "server";
            }

            if (userToBlock.equals(client.getInfo("loginID"))) {    //자기 자신에 대해선 못하도록 함.
                try {
                    client.sendToClient("Cannot block the sending of messages to yourself.");
                } catch (IOException ex) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            } else {    //타인을 블록하는 경우
                if (isLoginUsed(userToBlock) || userToBlock.equals("server")) { //현재 사용되는 로그인아이디거나 서버를 블록하는 경우(유효한 경우)
                    if (isLoginBeingUsed(userToBlock, false) && !userToBlock.equals("server")) {    //현재 로그인중이고 서버를 블록하지 않는 경우(이 또한 유효한 경우)
                        ConnectionToClient toBlock = getClient(userToBlock);
                        
                        if (((String)(toBlock.getInfo("fwdClient"))).equals(((String)(client.getInfo("loginID"))))) {   //블록을 당하는 클라이언트가 블록을 하는 자신에게 메시지를 전달하고 있는지 
                            toBlock.setInfo("fwdClient", "");   //블록을 당하는 클라이언트의 메시지 전달 초기화

                            try {   //메시지 전달이 취소되었음을 당사자들에게 알림
                                toBlock.sendToClient("Forwarding to "
                                    + client.getInfo("loginID")
                                    + " has been cancelled because "
                                    + client.getInfo("loginID") + " is now blocking messages from you.");
                                client.sendToClient("Forwarding from "
                                + toBlock.getInfo("loginID") + " to you has been terminated.");
                            } catch (IOException ioe) {
                                serverUI.display("Warning: Error sending message.");
                            }
                        }
                    }
                    addBlock = (Vector)(client.getInfo("blockedUsers"));    //블록을 하는 클라이언트의 블록할 목록을 가져와
                    addBlock.addElement(userToBlock);   //그 목록에 요소로 넣음
                } else {    //사용되지 않는 로그인 아이디의 경우
                    try {   //해당 클라이언트가 존재하지 않음을 알리고 메소드 종료
                        client.sendToClient("User " + userToBlock + " does not exist.");
                    } catch (IOException ioe) {
                        serverUI.display("Warning: Error sending message.");
                    }
                    return;
                }

                try {   //블록 사실을 본인에게 알림
                    client.sendToClient("Messages from " + userToBlock + " will be blocked.");
                } catch (IOException ex) {
                    serverUI.display("Warning: Error sending message.");
                }
            } 
        } catch (StringIndexOutOfBoundsException e) {   //블록 메소드의 경우 특정 로그인 아이디를 덧붙이지 않으면 잘못된 명령어 입력이 됨.
            try {
                client.sendToClient("ERROR - usage #block <loginID>");
            } catch (IOException ex) {
                serverUI.display("Warning: Error sending message.");
            }
        }
    }

    private void handleCmdFwd(String command, ConnectionToClient client) {  //메시지 전달을 다루는 메소드
        try {
            String destineeName = command.substring(5); //도착지의 인터넷 닉네임(=로그인아이디)
            
            try {
                if (destineeName.equals(client.getInfo("loginID"))) {   //자기 자신일 경우 취소
                    client.sendToClient("ERROR - Can't forward to self");
                    return;
                } else {   
                    if (destineeName.toLowerCase().equals("server")) {   //서버로의 전달은 불가능해 취소
                        client.sendToClient("ERROR - Can't forward to SERVER");
                        return;
                    } else {    
                        if (getClient(destineeName) == null) {  //없는 클라이언트일 경우 취소
                            client.sendToClient("ERROR - Client does not exist");
                            return;
                        }
                    }
                }
            } catch (IOException e) {
                serverUI.display("Warning: Error sending message.");
            }

            String tempFwdClient = (String)(client.getInfo("fwdClient"));   //자신이 이미 메시지를 전달하고 있는 클라이언트가 있을 수 있으므로 임시 저장
            ConnectionToClient destinee = getClient(destineeName);  //메세지를 전달할 클라이언트의 연결

            if(!(((Vector)(destinee.getInfo("blockedUsers"))).contains((String)(client.getInfo("loginID"))))) { //전달하려는 클라이언트가 자신을 블록하고 있지 않다면
                client.setInfo("fwdClient", destineeName);  //자신의 메시지 전달지를 해당 클라이언트로
            } else {    //블록하고 있다면 해당 메시지 표시
                try {
                    client.sendToClient("Cannot forward to " + destineeName + " because " + destineeName + " is blocking messages from you.");
                } catch (IOException e) {
                    serverUI.display("Warning: Error sending message.");
                }
                return;
            }

            try {
                if (isValidFwdClient(client)) { //전달이 타당한 클라이언트일 경우
                    client.sendToClient("Message will be forwarded to: " + client.getInfo("fwdClient"));
                } else {    //이전 설정대로
                    client.setInfo("fwdClient", tempFwdClient);
                    client.sendToClient("ERROR - Can't forward because a loop would result");
                }
            } catch (IOException e) { 
                serverUI.display("Warning: Error sending message.");
            }
        } catch (StringIndexOutOfBoundsException e) {   //전달지를 입력하지 않았을 경우 예외 발생
            try {
                client.sendToClient("ERROR - usage: #fwd <loginID>");
            } catch (IOException ex) {
                serverUI.display("Warning: Error sending message.");
            }
        }
    }
///////////
    private void handleCmdPub(String command, ConnectionToClient client) {
        String sender = "";

        try {
            sender = (String)(client.getInfo("loginID"));
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
                } else {
                    try {
                        client.sendToClient("Cannot send message because " + loginID + " is blocking messages from you.");
                    } catch (IOException e) {
                        serverUI.display("Warning: Error sending message.");
                    }
                }
            } else {
                try {
                    Thread[] clients = getClientConnections();

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
                            } else {
                                sendToClientOrServer(client, "Cannot send message because " + loginID + " is blocking message from you.");
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
    //누가 날 블록했는지(클라이언트가 사용하는 경우, 서버에서 메시지가 오는 경우)
    private void checkForBlocks(String login, ConnectionToClient client) {
        String results = "User block check:";

        if (!login.equals("server")) {
            if (blockedUsers.contains(login)) {
                results += "\nThe server is blocking message from you.";
            }
        }

        Thread[] clients = getClientConnections();

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
        //게스트로 로그인 할 경우 새 계정 만들기 실행
        if ((client.getInfo("loginID").equals("")) && (message.equals("guest"))) {
            client.setInfo("creatingNewAccount", new Boolean(true));

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
                            client.sendToClient("login already in use.  Enter login ID:");
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
                        if (character == LINEBREAK) {   //이후 줄바꿈 필요.
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
                    finally {
                        try {
                            c.close();
                        } catch (IOException ex) { }
                    }
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