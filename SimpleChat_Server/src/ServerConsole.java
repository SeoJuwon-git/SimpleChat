import java.util.*;
import java.io.*;
import server.*;
import common.*;
import ocsf.server.ObservableOriginatorServer;

public class ServerConsole implements ChatIF {
    final public static int DEFAULT_PORT = 5555;

    EchoServer server;  //서버 콘솔은 에코 서버를 가지고 있음

    public ServerConsole(ObservableOriginatorServer ooserver) throws IOException { //서버도 콘솔 입력이 가능해지도록 함에 따라 에코서버와 콘솔 사이의 문맥이 교환되고 콘솔 생성자에서 서버 객체가 같이 생성되도록 됨.
        server = new EchoServer(ooserver, this);
    }

    public void display(String message) {
        System.out.println(message);
    }

    public void accept() {  
        try {
            BufferedReader fromConsole = new BufferedReader(new InputStreamReader(System.in));
            String message;
            try {
                while (true) {  //서버도 콘솔의 입력을 받기 위해 무한 반복
                    message = fromConsole.readLine();
                    server.handleMessageFromServerUI(message);
                }
            } catch (NullPointerException e) { }
        } catch (Exception ex) {    //널포인터 예외를 제외한 예외 상황의 에러 스택 출력 및 메시지 표시
            ex.printStackTrace();
            display("ERROR!");
        }
    }

    public static void main(String[] args) {
        int port = 0;

        try {   //포트 번호를 파라미터로 받거나 기본 포트 사용
            port = Integer.parseInt(args[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            port = DEFAULT_PORT;
        }

        try {
            ObservableOriginatorServer ooserver = new ObservableOriginatorServer(port);
            ServerConsole sv = new ServerConsole(port); //서버 콘솔 객체 생성 및 입력 대기
            sv.accept();
        } catch (IOException e) {
            System.out.println("Could not start listening for clients.");
        }
    }
}
