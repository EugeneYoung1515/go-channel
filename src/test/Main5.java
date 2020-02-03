package test;

import comple.Channel2;
import comple.Select;
import comple.SemaphoreChannel;
import comple.SemaphoreSelect;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main5 {

    //主要是修改buffer 0 1 2
    public static void main(String[] args) {
        //Channel2 channel = new Channel2(1);
        SemaphoreChannel channel = new SemaphoreChannel(2);

        ConcurrentHashMap<Integer,AtomicInteger> concurrentHashMap = new ConcurrentHashMap<>(16);

        for (int i = 0; i < 3; i++) {
            new Thread(()->{

                for (int j = 0; j < 1000; j++) {

/*
                    Select.SelectResult result = new Select()
                            .receive(channel).select();
*/


                    SemaphoreSelect.SelectResult result = new SemaphoreSelect()
                            .receive(channel).select();


                    concurrentHashMap.computeIfAbsent((Integer)result.result,k->new AtomicInteger()).incrementAndGet();
                }

            }).start();
        }

        for (int i = 0; i < 3; i++) {
            new Thread(()->{
                for (int j = 0; j < 1000; j++) {
                    channel.send(j);
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
