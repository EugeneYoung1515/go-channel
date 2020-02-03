package test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class CyclicBarrierTest {
    public static void main(String[] args) {
        int n = 3;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(n);
        for (int i = 0; i < n; i++) {
            new Thread(()->{

                try {
                    Thread.sleep(4000);
                }catch (InterruptedException ex){
                    ex.printStackTrace();
                }

                try {
                    if(cyclicBarrier.await()==0){
                        System.out.println("done");
                    }
                }catch (InterruptedException|BrokenBarrierException ex){
                    ex.printStackTrace();
                }

            }).start();
        }
    }
}
