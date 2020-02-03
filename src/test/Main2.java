package test;

import comple.Channel2;
import comple.SemaphoreChannel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main2 {

    //主要是改buffer 在0 1 2中改
    public static void main(String[] args) {
        //Channel2 channel = new Channel2(2);
        SemaphoreChannel channel = new SemaphoreChannel(2);

        for (int j = 0; j < 3; j++) {
            new Thread(()->{

                for (int i = 0; i < 125; i++) {
                    channel.send(i);
                }
            }).start();
        }

        try {
            Thread.sleep(200);
        }catch (InterruptedException ex){
            ex.printStackTrace();
        }


        ConcurrentHashMap<Integer,AtomicInteger> concurrentHashMap = new ConcurrentHashMap<>(16);

        for (int i = 0; i < 3; i++) {
            new Thread(()->{

                for (int j = 0; j < 125; j++) {
                    //System.out.println(channel.receive());
                    concurrentHashMap.computeIfAbsent((Integer)channel.receive(),k->new AtomicInteger()).incrementAndGet();
                }

            }).start();
        }

        try {
            Thread.sleep(3000);
        }catch (InterruptedException ex){
            ex.printStackTrace();
        }

        System.out.println("size: "+concurrentHashMap.size());
        concurrentHashMap.forEach((k,v)->{
            if(v.get()!=3){
                System.out.println(k);
            }
        });
    }
}
