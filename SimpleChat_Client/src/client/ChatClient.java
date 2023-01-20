package client;

import ocsf.client.*;
import common.*;
import java.io.*;

public class ChatClient extends AbstractClient {

    ChatIF clientUI;

    public ChatClient(String host, int port, ChatIF clientUI) {
        super(host, port);
        this.clientUI = clientUI;

        try {
            openConnection();   //콘솔 프로그램 시작 시 서버에의 연결 시도
        } catch (IOException e) {
            handleMessageFromClientUI("#logoff");
            clientUI.display("Cannot open connection.  Awaiting command.");  //정상적으로 연결되지 않았을 경우
        }
    }

    public void handleMessageFromServer(Object msg) {   //서버에서의 메시지를 받으면 무조건으로 콘솔에 표시
        clientUI.display(msg.toString());
    }
    
    public void handleMessageFromClientUI(String message) { //자신이 입력하는 메시지에 대해서
        if (message.startsWith("#login") ) {
            try {
                openConnection();   //연결 시도
            } catch (IOException e) {   //로그인 시 연결을 실패했을 경우
                clientUI.display("Cannot establish connection.  Awaiting command.");
            }
            return; //심플채팅2와의 차이점. 이전에는 연결할 수 없었을 때 메소드를 종료했으나 지금은 맨 아래의 Invalid command로 가지 못하게 하는 차이.
        }

        if (message.startsWith("#quit"))    //종료 명령어인 경우 그에 대한 메소드 실행
            quit();

        if (message.startsWith("#logoff"))  {   //로그오프에 대한 명령어인 경우
            try {
                closeConnection();  //연결 해제 시도
            } catch (IOException e) {   //연결 해제가 실패할 경우 프로그램 강제 종료
                clientUI.display("Cannot logoff normally.  Terminating client.");
                quit();
            }
            return;
        }

        if (message.startsWith("#gethost")) {   //현재 호스트 표시 명령어인 경우
            clientUI.display("Current host: " + getHost());
            return;
        }

        if (message.startsWith("#getport")) {   //현재 포트 번호 표시 명령어인 경우
            clientUI.display("Current port: " + getPort());
            return;
        }

        if (message.startsWith("#sethost")) {   //호스트 변경 명령어인 경우
            if (isConnected())  {  //연결이 있는 경우 호스트가 변경되지 않도록(요구사항)
                clientUI.display("Cannot change host while connected.");
                return;
            }  
            //연결이 없는 경우
            try {
                setHost(message.substring(9));  //#sethost <host>에서 <host>부터인 메시지의 9번째 문자부터를
                clientUI.display("Host set to: " + getHost());  //변경된 호스트 자신에게 표시
            } catch(IndexOutOfBoundsException e) {  //변경할 호스트를 쓰지 않은 경우임(띄어쓰기 후 아무것도 입력이 없어도 변경되는 문제점->ip형식을 지키도록?)
                clientUI.display("Invalid host.  Use #sethost <host>.");
            } 
            return;
        }

        if (message.startsWith("#setport")) {   //포트 번호 변경 명령어인 경우
            if (isConnected()) { //똑같이 연결이 있을 경우 변경되지 않도록(요구사항)
                clientUI.display("Cannot change port while connected.");
                return;
            }
            
            try {
                int port = 0;
                port = Integer.parseInt(message.substring(9));

                if((port < 1024) || (port > 65535)) {   //포트 번호의 범위 안인지, well-known 포트가 아닌지 확인해 부적합하면 메시지만 표시
                    clientUI.display("Invalid port number.  Port unchanged");
                    return;
                }
                //정상적인 포트 번호라면 변경 및 자신에게 표시
                setPort(port);
                clientUI.display("Port set to " + port);
                return;
            } catch(Exception e) {  //호스트 변경과 달리 아무 입력도 없을 경우도 걸러져 에러 처리가 됨.
                clientUI.display("Invalid port.  Use #setport <port>.");
                clientUI.display("Port unchanged.");
            }
        }

        if (message.startsWith("#help") || message.startsWith("#?")) {
            clientUI.display("\nClient-side command list:"
            + "\n#block <loginID> -- Block messages from the specified client."
            + "\n#channel <channel> -- Connects to the specified channel."
            + "\n#fwd <loginID> -- Forward all messages to the specified client"
            + "\n#getchannel -- Gets the channel the client is currently connected to."
            + "\n#gethost -- Gets the host to which the client will connect/is connected."
            + "\n#getport -- Gets the port on which the client will connect/is connected."
            + "\n#help OR #? -- Lists all commands and their use."
            + "\n#login -- Connects to a server."
            + "\n#logoff -- Disconnects from a server."
            + "\n#nochannel -- Returns the client to the main channel."
            + "\n#private <loginID> <msg> -- Sends a private message to the specified client."
            + "\n#pub -- Sends a public message."
            + "\n#quit -- Terminates the client and disconnects from server."
            + "\n#sethost <newhost> -- Specify the host to connect to."
            + "\n#setport <newport> -- Specify the por Unblock messages from all blocked clients."
            + "\n#unblock -- Unblock messages from all blocked clients."
            + "\n#unblock <loginID> -- Unblock messages from a specific client."
            + "\n#unfwd -- Stop forwarding messages."
            + "\n#whoblocksme -- List all the users who are blocking messages from you."
            + "\n#whoiblock -- List all users you are blocking message from."
            + "\n#whoison -- Gets a list of all users and the channel they are connected to.");
            return;
        }

        if ((!(message.startsWith("#")))    //일반 메시지거나 서버의 도움이 필요한 명령어들의 경우
            || message.startsWith("#whoison")   //현재 채널에 누가 있는지
            || message.startsWith("#private")   //개인적인 메시지
            || message.startsWith("#channel")   //채널 바꾸기
            || message.startsWith("#pub")   //공적인 메시지
            || message.startsWith("#nochannel") //메인 채널로
            || message.startsWith("#getchannel")    //현재 채널
            || message.startsWith("#fwd")   //메시지 전달
            || message.startsWith("#unfwd") //메시지 전달 취소
            || message.startsWith("#block") //블록
            || message.startsWith("#unblock")   //블록해제
            || message.startsWith("#whoiblock") //내가 블록한
            || message.startsWith("#whoblocksme")) {    //나를 블록한
            
            try {
                sendToServer(message);  //위 종류들의 메시지들은 서버로 전송하여 처리함.
            } catch (IOException e) {   //전송 실패 및 연결 종료. 이 또한 실패시 관련 메시지 표시 후 프로그램 종료.
                clientUI.display("Cannot send the message to the server.  Disconnecting.");

                try {
                    closeConnection();
                } catch (IOException ex) {
                    clientUI.display("Cannot logoff normally.  Terminating client.");
                    quit();
                }
            return;
            }
            return;
        }
        //잘못된 # 명령어의 메시지들.
        clientUI.display("Invalid command.");
    }

    public void quit() {    //종료 과정(연결 끊기->시스템 종료)
        try {
            closeConnection();
        } catch (IOException e) { }
        System.exit(0);
    }

    protected void connectionClosed(boolean isAbnormal) {   //연결을 끊는 경우 그것이 정상인지 비정상 해제인지를 클라이언트에게 메시지로 표시하는 메소드
        if (isAbnormal)  {
            clientUI.display("Abnormal termination of connection.");
            return;
        }
        clientUI.display("Connection closed.");
        return;
    }

    protected void connectionEstablished() {    //openConnection()이 정상적으로 이루어졌을 때 클라이언트에게 표시할 수 있는 메시지로 사용되는 ocsf 메소드 구현
        clientUI.display("Connection established with " + getHost() + " on port "+ getPort());
    }

    protected void connectionException(Exception exception) {  //서버가 연결을 끊었을 때의 메시지로 사용되는 ocsf 메소드 구현
        clientUI.display("Connection to server terminated.");
    }
}