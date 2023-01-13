package client;

import ocsf.client.*;
import common.*;
import java.io.*;

public class ChatClient extends AbstractClient {
    ChatIF clientUI;

    String loginID; //이번에 추가된 로그인 아이디

    public ChatClient(String host, int port, String login, ChatIF clientUI) {
        super(host, port);
        this.clientUI = clientUI;
        loginID = login;    //콘솔에서 생성된 대로 로그인 아이디를 변수로 저장

        try {
            openConnection();   //서버에의 연결 시도
            sendToServer("#login " + login);    //성공시 서버에 로그인 했음을 메시지로 보냄
        } catch (IOException e) {
            clientUI.display("Cannot open connection. Awaiting command.");  //정상적으로 연결되지 않았을 경우
        }
    }

    public void handleMessageFromServer(Object msg) {   //서버에서의 메시지를 받으면 무조건으로 콘솔에 표시
        clientUI.display(msg.toString());
    }
    
    public void handleMessageFromClientUI(String message) { //자신이 입력하는 메시지에 대해서
        if (message.startsWith("#login") && !isConnected()) {   //로그인 시도 명령어일 경우+처음 시도일 경우만(요구사항)
            try {
                openConnection();   //연결 시도
            } catch (IOException e) {   //로그인 시 연결을 실패했을 경우
                clientUI.display("Cannot establish connection." + " Awaiting command.");
                return;
            }
        }

        if (message.startsWith("#quit"))    //종료 명령어인 경우 그에 대한 메소드 실행
            quit();

        if (message.startsWith("#logoff"))  {   //로그오프에 대한 명령어인 경우
            try {
                closeConnection();  //연결 해제 시도
            } catch (IOException e) {   //연결 해제가 실패할 경우 프로그램 강제 종료
                clientUI.display("Cannot logoff normally. Terminating client.");
                quit();
            }

            connectionClosed(false);    //연결 해제 후 정상 혹은 비정상 해제인지에 따른 메시지 표시
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
            if (isConnected())  //연결이 있는 경우 호스트가 변경되지 않도록(요구사항)
                clientUI.display("Cannot change host while connected.");
            else {  //연결이 없는 경우
                try {
                    setHost(message.substring(9));  //#sethost <host>에서 <host>부터인 메시지의 9번째 문자부터를
                    clientUI.display("Host set to: " + getHost());  //변경된 호스트 자신에게 표시
                } catch(IndexOutOfBoundsException e) {  //변경할 호스트를 쓰지 않은 경우임(띄어쓰기 후 아무것도 입력이 없어도 변경되는 문제점->ip형식을 지키도록?)
                    clientUI.display("Invalid host. Use #sethost <host>.");
                }
            }
            return;
        }

        if (message.startsWith("#setport")) {   //포트 번호 변경 명령어인 경우
            if (isConnected())  //똑같이 연결이 있을 경우 변경되지 않도록(요구사항)
                clientUI.display("Cannot change port while connected.");
            else {
                try {
                    int port = 0;
                    port = Integer.parseInt(message.substring(9));

                    if((port < 1024) || (port > 65535)) {   //포트 번호의 범위 안인지, well-known 포트가 아닌지
                        clientUI.display("Invalid port number. Port unchanged");
                    } else {    //정상적인 포트 번호라면 변경 및 자신에게 표시
                        setPort(port);
                        clientUI.display("Port set to " + port);
                    }
                } catch(Exception e) {  //호스트 변경과 달리 아무 입력도 없을 경우도 걸러져 에러 처리가 됨.
                    clientUI.display("Invalid port. Use #setport <port>.");
                    clientUI.display("Port unchanged.");
                }
            }
            return;
        }
        //로그인(위에선 연결시도만 하고 return;가 없음) 혹은 메시지 전송일 경우에 대해서
        if ((message.startsWith("#login")) || (!(message.startsWith("#")))) {
            try {
                sendToServer(message);  //클라이언트에서 처리하는 것이 아니라 서버로 보내짐(요구사항)
            } catch (IOException e) {   //로그인 혹은 메시지 전송 시도가 실패할 경우 연결 끊음 시도
                clientUI.display("Cannot send the message to the server." + " Disconnecting."); 

                try {
                    closeConnection();  //연결 끊음 시도
                }catch (IOException ex) { //실패 시 프로그램 종료
                    clientUI.display("Cannot logoff normally. Terminating client.");
                    quit();
                }
            }
        }else { //위의 올바른 명령들도 아니고, 메시지 전송도 아닌 경우 = 올바르지 않은 명령어만.
            clientUI.display("Invalid command");
        } 
    }

    public void quit() {    //종료 과정(연결 끊기->시스템 종료)
        try {
            closeConnection();
        } catch (IOException e) { }
        System.exit(0);
    }

    protected void connectionClosed(boolean isAbnormal) {   //연결을 끊는 경우 그것이 정상인지 비정상 해제인지를 클라이언트에게 메시지로 표시하는 메소드
        if (isAbnormal)
            clientUI.display("Abnormal termination of connection.");
        else
            clientUI.display("Connection closed.");
    }

    protected void connectionEstablished() {    //openConnection()이 정상적으로 이루어졌을 때 클라이언트에게 표시할 수 있는 메시지로 추측되나 사용되지 않은 것 같음
        clientUI.display("Connection established with " + getHost() + " on port "+ getPort());
    }

    protected void connectionException(Exception exception) {  //어떠한 예외로 서버에의 연결을 끊었을 때의 메시지로 추정되나 사용되지 않은 것 같음
        clientUI.display("Connection to sever terminated.");
    }
}
