package server;

import java.io.*;
import ocsf.server.*;
import common.*;

public class EchoServer extends AbstractServer {
    final public static int DEFAULT_PORT = 5555;

    ChatIF serverUI;

    private boolean closing = false;    //서버의 연결이 닫히고 있는 중인지에 대한 변수로 false, true의 변화는 있으나 아직 조건문에 사용되지는 않음

    public EchoServer(int port, ChatIF serverUI) throws IOException {   //콘솔 객체 생성 동시에 생성되며 서버 UI가 있음. 생성 즉시 클라이언트의 연결 요청을 받을 수 있는 리슨 상태가 됨(이전 코드와의 차이점)
        super(port);
        this.serverUI = serverUI;
        listen();   //listen()으로 인해 추가 스레드 생성됨. 클라이언트와의 연결시 추가적으로 스레드가 생성되나 해제시 해당 스레드가 사라짐
    }
    
    public void handleMessageFromClient(Object msg, ConnectionToClient client) {    //ocsf의 메소드가 구현되는 건 같으나 단순히 모든 클라이언트에게 메시지를 반환하는 것만이 아닌 로그인 요청에 대한 메시지 처리
        String command = (String)msg;
        
        serverUI.display("Message : \"" +command +"\" from " + client.getInfo("loginID"));  //받은 메시지 정보 표시

        if (command.startsWith("#login")) { //클라이언트의 로그인 요청이었을 경우
            if (client.getInfo("loginID") == null) {    //로그인 ID가 null이라면(클라이언트 쪽에선 로그인 아이디가 없으면 프로그램을 실행시켜도 바로 종료되긴 하나 악의적 접속이 있을 수 있을 듯함. 아니면 입력 환경 차이?)
                try {
                    client.setInfo("loginID", command.substring(7));    //"#loginID "뒤부터 클라이언트의 loginID 변수로 저장 및 서버, 모든 클라이언트에게 해당 로그인 사실을 전송
                    serverUI.display(client.getInfo("loginID") + " has logged on.");
                    sendToAllClients(client.getInfo("loginID") + " has logged on.");
                } catch (IndexOutOfBoundsException e) { //#login 뒤에 띄어쓰기도 없이 입력했을 경우에 해당 메시지가 표시
                    try {
                        client.sendToClient("ERROR - Invalid login command. Disconnecting.");
                    } catch (IOException ex) { }

                    try {   //접속한 클라이언트의 연결을 해제함.(예외 상황을 상세하게 하기 위해 try를 나눠놓은 것으로 추측)
                        client.close();
                    } catch (IOException exc) {
                        serverUI.display("ERROR - Cannot remove client.");
                    }
                    serverUI.display("No login, terminating client's connectiong.");    //서버 자신에게도 로그인이 되지 않았으며 클라이언트의 연결을 해제하였음을 표시
                }
            } else {    //로그인ID가 이미 있다면
                serverUI.display(client.getInfo("loginID") + " attempted to login twice."); //두 번째 로그인임을 서버 자신에게 표시
                try {
                    client.sendToClient("Cannot login twice."); //로그인을 시도한 클라이언트에게도 알림
                } catch (IOException e) {
                    try {
                        client.close(); //알림이 실패했을 경우 연결 해제 시도
                    } catch (IOException ex) {  //연결 해제 시도도 실패했을 경우의 서버 자신에의 메시지
                        serverUI.display("ERROR - Cannot remove client.");  
                    }
                }
            }
        } else {    //로그인 요청이 아니었을 경우
            if (client.getInfo("loginID") == null) {    //로그인 아이디가 없는 누군가에게서 보내진 것이었을 경우 서버 자신에게 알리고 연결을 해제할 것임을 메시지로 표시
                serverUI.display("Unknown client did not login. " + "Terminating connection.");

                try {   //메시지를 보낸 클라이언트에게도 이를 알림
                    client.sendToClient("No login recorded, disconnecting from server.");   
                } catch (IOException e) { }
                //로그인 아이디가 없지만 서버와의 연결이 있는 누군가에게서 보내진 것이므로 해당 클라이언트와의 연결을 해제 시도
                finally {
                    try {
                        client.close();
                    } catch (IOException ex) {
                        serverUI.display("ERROR - Cannot remove client.");  //동일
                    }
                }
            } else {    //로그인 요청이 아니며, 정상적인 로그인아이디를 소지한 클라이언트의 경우에만 에코 서버로서의 역할을 다함. 모든 클라이언트들에게 메시지 반환
                this.sendToAllClients(client.getInfo("loginID") + "> " + msg);
            }
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
        sendToAllClients("WARNING - Server has stopped accepting clients.");
    }

    protected void serverClosed() { //서버가 닫힐 때 서버에게 표시되는 메시지 메소드
        serverUI.display("Server is closed");
    }

    protected void clientConnected(ConnectionToClient client) { //새로운 클라이언트가 서버와의 연결을 시도할 때 서버에게 표시되는 메시지 메소드
        serverUI.display("A new client is attempting to connect " + "to the server.");
    }

    protected void clientDisconnected(ConnectionToClient client){   //서버와의 연결이 끊겼을 때 서버 및 다른 연결되어 있는 클라이언트들에게 보내는 메시지 메소드 
        disconnectionNotify(client);
    }

    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {   //예외가 발생된 클라이언트에게 위와 같이 해당 메시지를 보내는 메소드로 보이나 실제 동작 확인 어려움 
        disconnectionNotify(client);
    }

    public void handleMessageFromServerUI(String message) { //서버 자신이 입력한 메시지들에 대한 처리 메소드
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
            closing = true; //닫히고 있다는 불린
            try {   //모든 클라이언트들에게 이에 대한 메시지를 보내고 닫는 메소드 실행
                sendToAllClients("Server shutting down. You are being disconnected.");
                close();
            } catch (IOException e) {   //예외로 인해 닫히지 않는다면 이에 대한 메시지를 서버에게 표시하고 종료 시도
                serverUI.display("Cannot close normally. Terminating server.");
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
                serverUI.display("Cannot change port while clients " + "are connected or while server is listening");
            } else {
                try {
                    int port = 0;
                    port = Integer.parseInt(message.substring(9));  //"#setport " 이후의 문자열

                    if((port < 1024) || (port > 65535)) {   //well-known이거나 정상 포트 범위가 아닌 경우 
                        serverUI.display("Invalid port number. Port unchanged.");
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

        if (!(message.startsWith("#"))) {   //#으로 시작하지 않는 모든 입력은 서버가 하는 메시지 전송이 됨.
            serverUI.display("SERVER MESSAGE> " + message);
            sendToAllClients("SERVER MESSAGE> " + message);
        } else {    //메시지 전송도 아니고 올바른 명렁어도 아닌 경우(클라이언트의 코드와 비슷한 if문 구조인듯)
            serverUI.display("Invalid command.");
        }
    }

    private void disconnectionNotify(ConnectionToClient client) {   //연결을 해제당한 클라이언트(null은 제외)를 제외하고, 서버 및 연결된 클라이언트들에게 메세지를 보내는 메소드
        if (client.getInfo("loginID") != null) {
            sendToAllClients(client.getInfo("loginID") +" has disconnected.");
            serverUI.display(client.getInfo("loginID") +" has disconnected.");
        }
    }
}