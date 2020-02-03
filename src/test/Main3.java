package test;

import comple.Channel2;
import comple.SemaphoreChannel;

import java.util.concurrent.Semaphore;

public class Main3 {

    //send -->receive -->close -->receive -->receive
    public static void main(String[] args) {
        Channel2 channel = new Channel2(0);
        //SemaphoreChannel channel = new SemaphoreChannel(0);

        Semaphore semaphore = new Semaphore(0);
        Semaphore semaphore1 = new Semaphore(0);

        new Thread(()->{
            try {
                Thread.sleep(1000);
            }catch (InterruptedException ex){
                ex.printStackTrace();
            }

            channel.send("1");
           try {
               semaphore.acquire();
           }catch (InterruptedException ex){
               ex.printStackTrace();
           }

           try {
               Thread.sleep(2000);
           }catch (InterruptedException ex){
               ex.printStackTrace();
           }
            channel.close();
        }).start();

        System.out.println(channel.receive());
        semaphore.release();
        System.out.println(channel.receive());
        System.out.println(channel.receive());
    }
}
