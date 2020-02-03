package test;

import comple.Channel2;
import comple.Select;

public class Main4 {
    public static void main(String[] args) {
        Channel2 channel2 = new Channel2(0);
        new Thread(()->{
            Select.SelectResult result = new Select().receive(channel2).select();
            System.out.println(result.result);
        }).start();

        try {
            Thread.sleep(4000);
        }catch (InterruptedException ex){
            ex.printStackTrace();
        }

        channel2.send("1");
    }
}
