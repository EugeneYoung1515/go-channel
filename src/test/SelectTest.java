package test;

import comple.Channel2;
import comple.Select;
import comple.SemaphoreChannel;
import comple.SemaphoreSelect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static sun.awt.geom.Crossings.debug;

public class SelectTest {

    static void selectWithTwoChan() throws InterruptedException {
        //selectWithNChan(2, 50, 50, 2, 0, 300, false);
        //selectWithNChan(2, 50, 50, 0, 2, 300, false);

        selectWithNChan(2, 50, 50, 1, 1, 300, false);

        //selectWithNChan(2, 50, 50, 1, 1, 300, false);

        //selectWithNChan(2, 50, 50, 2, 0, 300, true);
        //selectWithNChan(2, 50, 50, 0, 2, 300, true);
        //selectWithNChan(2, 50, 50, 1, 1, 300, true);
    }


    public static void main(String[] s) throws InterruptedException{
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            selectWithTwoChan();

            //addSameChannelMoreThanOne_selectSendFirst_expectRandom();

            //addSameChannelMoreThanOne_selectRecieveFirst_expectRandom();
            System.out.println("fi "+i);
        }
        System.out.println(System.currentTimeMillis()-start);
        //Runtime.getRuntime().exit(0);
    }

    private static class Holder{
        private Integer i;
        private Object index = new Object();

        public Holder(Integer i) {
            this.i = i;
        }

        @Override
        public String toString() {
            return "Holder{" +
                    "i=" + i +
                    ", index=" + index +
                    '}';
        }
    }

    static void selectWithNChan(int chanCnt, int threadPerChan, int testPerThread, int sendCnt,
                                int receiveCnt, int interval,
                                boolean hasDefault) throws InterruptedException {
        assert chanCnt == sendCnt + receiveCnt;
        int selectCnt = chanCnt * threadPerChan * testPerThread;

        ExecutorService executorService = Executors.newFixedThreadPool(chanCnt * threadPerChan);


        //Channel2<Holder>[] chans = ((Channel2<Holder>[]) new Channel2[chanCnt]);
            SemaphoreChannel<Holder>[] chans = ((SemaphoreChannel<Holder>[]) new SemaphoreChannel[chanCnt]);

        for (int j = 0; j < chans.length; j++) {
            //chans[j] = new Channel2<>(2);
                chans[j] = new SemaphoreChannel<>(0);
        }

        CountDownLatch latch = new CountDownLatch(selectCnt);

        //List<Integer> receiveFromSelector = Collections.synchronizedList(new ArrayList<>());
        //List<Integer> receiveFromSenderChan = Collections.synchronizedList(new ArrayList<>());
        //List<Integer> receiveFromSelectorDefault = Collections.synchronizedList(new ArrayList<>());

        List<Holder> receiveFromSelector = Collections.synchronizedList(new ArrayList<>());
        List<Holder> receiveFromSenderChan = Collections.synchronizedList(new ArrayList<>());
        List<Holder> receiveFromSelectorDefault = Collections.synchronizedList(new ArrayList<>());


        int tmpSendCnt = sendCnt;
        int tmpReceiveCnt = receiveCnt;

        //下面测试的作用是
        //比如一个channel 一个线程 一个线程里三次读或者三次写
        //两个channel 就是六次读或写
        //一次select 只能完成其中一个case 所以需要六次循环

        for (int i = 0; i < chanCnt; i++) {
            int finalI = i;
            //Channel2<Holder> chan = chans[i];
                SemaphoreChannel<Holder> chan = chans[i];

            final boolean isSend = --tmpSendCnt >= 0 || --tmpReceiveCnt < 0;
            for (int j = 0; j < threadPerChan; j++) {
                executorService.execute(() -> {
                    for (int k = 0; k < testPerThread; k++) {
                        if (isSend) {
                            if(debug){
                                System.out.println("send");
                            }
                            //chan.send(finalI * threadPerChan * testPerThread + k);
                            chan.send(new Holder(finalI * threadPerChan * testPerThread + k));
                        }
                        else {
                            if(debug){
                                System.out.println("read");
                            }
                            receiveFromSelector.add(chan.receive());
                        }
                        latch.countDown();
                        if (interval > 0) {
                            try {
                                TimeUnit.MICROSECONDS.sleep(interval);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }

        executorService.shutdown();


        try {

            //System.out.println(selectCnt);
            for (int i = 0; i < selectCnt; i++) {
                if(debug){
                    System.out.println(i);
                }

                //Select<Integer> select2 = new Select<>();
                //Select<Holder> select2 = new Select<>();
                    SemaphoreSelect<Holder> select2 = new SemaphoreSelect<>();

                for (int j = 0; j < sendCnt; j++) {
                    select2.receive(chans[j]);
                }
                for (int j = 0; j < receiveCnt; j++) {
                    //select2.send(chans[sendCnt+j],i);
                    select2.send(chans[sendCnt+j],new Holder(i));
                }

                //Select.SelectResult<Integer> result;
                //Select.SelectResult<Holder> result;
                    SemaphoreSelect.SelectResult<Holder> result;
                if(hasDefault){
                    result = select2.trySelect();
                }else{
                    result = select2.select();
                }
                //System.out.println(result.result);
                if(result!=null){
                    //System.out.println(result.index);
                    if(result.index<sendCnt){
                        if(result.result==null){
                            System.out.println("shit");
                        }
                        //System.out.println(result.result);
                        receiveFromSenderChan.add(result.result);
                    }
                }else{
                    //receiveFromSelectorDefault.add(i);
                    receiveFromSelectorDefault.add(new Holder(i));
                    latch.countDown();
                }

            }


        }catch (Exception e){
            e.printStackTrace();
        }

        latch.await();

        if(debug){
            System.out.println("len");
            for (int j = 0; j < chans.length; j++) {
                System.out.println(chans[j].getBufLength());
            }

            for (int j = 0; j < chans.length; j++) {
                int l = chans[j].getBufLength();
                for (int i = 0; i < l; i++) {
                    System.out.println(chans[j]+" "+chans[j].getBuf().take());
                }
            }
        }


        assertEquals(selectCnt,
                receiveFromSelector.size() + receiveFromSenderChan.size() + receiveFromSelectorDefault.size());
        assertTrue(receiveFromSelector.stream().allMatch(data -> data != null /*&& data < selectCnt*/&& data.i < selectCnt));
        if (!receiveFromSenderChan.stream().allMatch(data -> data != null /*&& data < selectCnt*/&& data.i < selectCnt)){
            throw new RuntimeException("fail");
        }
    }


    public static void assertEquals(int i,int j){
        if(i!=j){
            throw new RuntimeException("not equal");
        }
    }

    public static void assertTrue(boolean t){
        if(!t){
            throw new RuntimeException("not true");
        }
    }
}
