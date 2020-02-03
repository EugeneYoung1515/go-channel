package test;

import comple.Channel2;

public class Main {
    public static void main(String[] args) {
        Channel2 channel = new Channel2(0);

        for (int i = 0; i < 3; i++) {
            int m = i;
            new Thread(()->{

                channel.send(m);

            }).start();
        }

        new Thread(()->{

            try {
                Thread.sleep(3000);
            }catch (InterruptedException ex){
                ex.printStackTrace();
            }

            channel.close();

        }).start();

        try {
            Thread.sleep(4000);
        }catch (InterruptedException ex){
            ex.printStackTrace();
        }

        for (int i = 0; i < 4; i++) {
            System.out.println(channel.receive());
        }


    }
}
