import java.io.*;
import client.*;
import common.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ClientConsole implements ChatIF{
    
    private static Logger log = LogManager.getLogger(ClientConsole.class);

    final public static int DEFAULT_PORT = 5555;

    ChatClient client;

    public ClientConsole(String host, int port) { //생성자에서 클라이언트 객체 생성 및 호스트, 포트, 자신의 문맥을 넘겨줌
        client = new ChatClient(host, port, this);
        log.debug("ClientConsole\'s Cunstructor initializes.");
    }

    public void accept() {  //채팅 입력 대기 함수
        try {
            BufferedReader fromConsole = new BufferedReader(new InputStreamReader(System.in));
        
                String message;

                try {
                    while (true) {      //채팅중. 무한 반복
                        message = fromConsole.readLine();
                        client.handleMessageFromClientUI(message);
                    }
                } catch (NullPointerException e) { 

                }
        } catch (Exception ex) {
            System.out.println("Unexpected error while reading from console!");
            log.error("Unexpected error while reading from console!", ex);
        }
    }

    public void display(String message) {   //클라이언트 콘솔에 표시하는 형식에 대한 메소드
        System.out.println(message);
    }

    public static void main(String[] args) {
        log.info("ClientConsole Program start.");
        String host = "";
        int port = 0;

        try {
            host = args[0]; //첫 번째 파라미터를 호스트로 받음. 호스트는 dns 또는 ip주소. 클라이언트가 서버에 접속하기 위해선 서버의 ip주소가 필요.
            log.info("args[0] exist: "+host);
        } catch (ArrayIndexOutOfBoundsException e) { //파라미터 입력이 없었다면 자기 자신(localhost)으로.
           host = "localhost";
           log.info("args[0] does not exist: "+host, e);
        }
        
        try {
            port = Integer.parseInt(args[1]);   //두 번째 파라미터를 포트 번호로 받음. 문자열로 들어오므로 변환 필요
            log.info("args[1] exist: "+port);
        } catch (Throwable t) {
            port = DEFAULT_PORT;    //기본 포트 번호
            log.info("args[1] does not exist: "+port, t);
        }

        ClientConsole chat = new ClientConsole(host, port);
        chat.accept();
    }
}
