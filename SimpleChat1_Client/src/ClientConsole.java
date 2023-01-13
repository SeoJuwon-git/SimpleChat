import java.io.*;
import client.*;
import common.*;

public class ClientConsole implements ChatIF{
    final public static int DEFAULT_PORT = 5555;    //기본 포트 설정

    ChatClient client;  //채팅을 하는 클라이언트 본인. 콘솔은 자신을 사용하는 클라이언트를 객체로 가지고 있음

    public ClientConsole(String host, int port)
    {
        try {
            client = new ChatClient(host, port, this);  //호스트명, 포트, 콘솔 자신의 정보로 채팅 클라이언트 객체 생성
        } catch(IOException exception) {    //입출력 오류로 인해 연결 불가. 프로그램 종료
            System.out.println("Error: Can't setup connection!" + " Terminating client.");
            System.exit(1);     //비정상종료
        }
    }

    public void accept()
    {
        try{
            BufferedReader fromConsole = new BufferedReader(new InputStreamReader(System.in));  //입력 스트림 읽어 버퍼스트림으로 저장

            String message;

            while (true){   //프로그램 실행 후 연결이 정상적으로 이루어진다면 이 부분을 반복함.
                message = fromConsole.readLine();   //한 줄마다 읽어
                client.handleMessageFromClientUI(message);  //서버로 메시지 전송하는 함수 호출
            }
        } catch(Exception ex){
            System.out.println("Unexpected error while reading from console!");
        }
    }
    
    public void display(String message) //ChatIF의 추상 함수 구현. 밑 형식으로 메시지 표시
    {
        System.out.println("> " + message);
    }

    public static void main(String[] args)
    {
        String host = "";
        int port = 0;

        try{
            host = args[0]; //호스트명은 외부 매개변수 입력으로
        } catch (ArrayIndexOutOfBoundsException e) {    //매개변수 입력이 없어 배열 인덱스 오류시 자동으로 로컬호스트로 호스트명 지정
            host = "localhost";
        }
        //System.out.println(host);   //외부 매개변수로 받은 것이 없어 localhost로 되는 것 확인
        ClientConsole chat = new ClientConsole(host, DEFAULT_PORT); //호스트명과 기본포트로 콘솔 객체 생성
        //서버에선 매개변수로 입력되는 포트가 없을 때 기본 포트를 사용하지만 여기선 그냥 기본포트를 사용하도록 되어있음
        chat.accept();  //연결이 제대로 이루어졌다면 채팅을 서버로 보내는 메소드 실행
    }
}
