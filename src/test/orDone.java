package test;

import comple.Channel2;
import comple.Select;
import comple.SemaphoreChannel;
import comple.SemaphoreSelect;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

//修改buffer 0 1 2
public class orDone {
    public static /*Channel2*/ SemaphoreChannel orDone(/*Channel2 done, Channel2 c*/ SemaphoreChannel done,SemaphoreChannel c){
        //Channel2 valStream = new Channel2(2);
        SemaphoreChannel valStream = new SemaphoreChannel(2);

        int n = 3;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(3);
        for (int i = 0; i < n; i++) {
            Thread thread = new Thread(()->{
                try {
                    while (true){
                        //Select.SelectResult result = new Select().receive(done).receive(c).select();

                        SemaphoreSelect.SelectResult result = new SemaphoreSelect().receive(done).receive(c).select();
                        if(result.index==0){
                            return;
                        }
                        if(result.index==1){
                            Object r = result.result;
                            if(r==null){
                                return;
                            }
                            //new Select().send(valStream,r).receive(done).select();
                            new SemaphoreSelect().send(valStream,r).receive(done).select();
                        }
                    }

                }finally {
                    //valStream.close();
                    try {
                        if(cyclicBarrier.await()==0){
                            valStream.close();
                        }
                    }catch (InterruptedException|BrokenBarrierException ex){
                        ex.printStackTrace();
                    }
                }
            });
            thread.setName("orDone"+i);
            thread.start();
        }
        return valStream;
    }

    public static void main(String[] args) {
        //Channel2 channel = new Channel2(2);
        SemaphoreChannel channel = new SemaphoreChannel(1);
        int n = 3;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(3);
        for (int j = 0; j < n; j++) {
            Thread thread = new Thread(()->{
                for (int i = 0; i < 10000; i++) {
                    //new Select().send(channel,i).select();
                    new SemaphoreSelect().send(channel,i).select();
                }
                //channel.close();
                try {
                    if(cyclicBarrier.await()==0){
                        channel.close();
                    }
                }catch (InterruptedException|BrokenBarrierException ex){
                    ex.printStackTrace();
                }

            });
            thread.setName("pro"+j);
            thread.start();
        }

        ConcurrentHashMap<Integer,AtomicInteger> concurrentHashMap = new ConcurrentHashMap<>(16);

        /*Channel2*/SemaphoreChannel x = orDone(/*new Channel2(0)*/new SemaphoreChannel(0),channel);
        for (int i = 0; i < 3; i++) {
            Thread thread1= new Thread(()->{

                while (true){
                    Object r = x.receive();
                    if(r==null){
                        return;
                    }
                    //System.out.println(r);
                    concurrentHashMap.computeIfAbsent((Integer)r,k->new AtomicInteger()).incrementAndGet();
                }

            });
            thread1.setName("print"+i);
            thread1.start();
        }

        try {
            Thread.sleep(7000);
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
