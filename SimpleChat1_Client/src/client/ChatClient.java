package client;

import ocsf.client.*;
import common.*;
import java.io.*;

public class ChatClient extends AbstractClient //ocsf의 추상 클래스를 상속하는 클라이언트 본인에 대한 클래스
{
    ChatIF clientUI;    //각 클라이언트마다 가지고 있는 채팅 UI. 자신이 소유한 클라이언트 콘솔을 객체로 가지고 있음

    public ChatClient(String host, int port, ChatIF clientUI) throws IOException
    {
        super(host, port);
        this.clientUI = clientUI;
        openConnection();   //입출력 오류가 없을 경우 연결 시도(ocsf의 함수로 연결을 가능하도록 설정하는 역할로 추측)
    }

    public void handleMessageFromServer(Object msg) //서버로부터 온 메시지를 다루기.(ocsf의 함수 구현임)
    {
        clientUI.display(msg.toString());   //온 메시지 바로 채팅창에 표시
    }

    public void handleMessageFromClientUI(String message)   //클라이언트UI로부터 온 메시지 다루기.
    {
        try {
            sendToServer(message);  //연결되어 있는 서버로 메시지를 전송하는 것으로 추측(ocsf의 함수)
        } catch(IOException e){ //입출력에 예외 발생
            clientUI.display("Could no send message to server. Terminating client.");   //서버로 메시지를 보낼 수 없음과 클라이언트 종료 표시.
            quit(); //종료 메소드 실행
        }
    }

    public void quit()
    {
        try {
            closeConnection();  //다시 연결 불가능 설정으로 추측(ocsf의 함수)
        } catch(IOException e) { }
        System.exit(0); //프로그램 종료
    }
}
