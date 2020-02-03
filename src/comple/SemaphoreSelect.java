package comple;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class SemaphoreSelect<T>{
    private int i;

    private List<Case<T>> list = new ArrayList<>(10);

    private static class Case<T>{
        private SemaphoreChannel<T> channel;
        private T sendData;
        private int index;

        private SemaphoreChannel.Node<T> node;

        public Case(SemaphoreChannel<T> channel, T sendData) {
            this.channel = channel;
            this.sendData = sendData;
        }

        public Case(SemaphoreChannel<T> channel) {
            this.channel = channel;
        }

        public Case(SemaphoreChannel<T> channel, T sendData, int index) {
            this.channel = channel;
            this.sendData = sendData;
            this.index = index;
        }

        public Case(SemaphoreChannel<T> channel, int index) {
            this.channel = channel;
            this.index = index;
        }
    }

    public SemaphoreSelect<T> send(SemaphoreChannel<T> channel, T sendData){
        list.add(new Case<>(channel,sendData,i));
        i++;

        return this;
    }

    public SemaphoreSelect<T> receive(SemaphoreChannel<T> channel){
        list.add(new Case<>(channel,i));
        i++;

        return this;
    }

    public SelectResult<T> select(){
        return selectInternal(false);
    }

    public SelectResult<T> trySelect(){
        return selectInternal(true);
    }

    private SelectResult<T> selectInternal(boolean nonBlocking){
        SemaphoreChannel.SignalAndResult<T> signalAndResult = new SemaphoreChannel.SignalAndResult<>();

        List<Case<T>> shuffle = new ArrayList<>(list);
        Collections.shuffle(shuffle);

        /*
        for(Case<T> _case:shuffle){
            _case.channel.lock();//这里多线程来就死锁了
        }
        */

        /*
        Set<ReentrantLock> set = new HashSet<>(16);
        for (Case<T> _case:shuffle){
            set.add(_case.channel.lock);
        }
        List<ReentrantLock> lockOrder = new ArrayList<>(set);
        for(ReentrantLock r:lockOrder){
            r.lock();
        }
        */

        Set<SemaphoreChannel<T>> lockOrder = new TreeSet<>(new Comparator<SemaphoreChannel<T>>() {
            @Override
            public int compare(SemaphoreChannel<T> o1, SemaphoreChannel<T> o2) {
                return o1.id-o2.id;
            }
        });
        for (Case<T> _case:shuffle){
            lockOrder.add(_case.channel);
        }

        for(SemaphoreChannel<T> channel:lockOrder){
            channel.lock.lock();
        }

        try {
            for (Case<T> _case:shuffle){
                if(_case.sendData!=null){
                    boolean success = _case.channel.put(_case.sendData,true);
                    if(success){
                        return new SelectResult<>(_case.index,_case.channel,null);
                    }
                }else{
                    T x = _case.channel.take(true);
                    if(x!=null){
                        return new SelectResult<>(_case.index,_case.channel,x);
                    }else{
                        if(_case.channel.isClosed){
                            System.out.println("close");
                            //return null;
                            return new SelectResult<>(_case.index,_case.channel,null);
                        }
                    }
                }
            }

            if (nonBlocking){
                return null;
            }


            for (Case<T> _case:shuffle){//这个for能不能和上面的for合并

                if(_case.sendData!=null){
                    SemaphoreChannel.Node<T> node = new SemaphoreChannel.Node<>(_case.sendData,signalAndResult);
                    node.index = _case.index;

                    _case.node = node;

                    _case.channel.registerSender(node);
                }else{
                    SemaphoreChannel.Node<T> node = new SemaphoreChannel.Node<>(signalAndResult);
                    node.index = _case.index;

                    _case.node = node;

                    _case.channel.registerReceiver(node);
                }
            }
        }finally {
            /*
            for(Case<T> _case:shuffle){
                _case.channel.unlock();
            }
            */

            /*
            for(ReentrantLock r:lockOrder){
                r.unlock();
            }
            */

            for(SemaphoreChannel<T> channel:lockOrder){
                channel.lock.unlock();
            }


        }



        try {

            //signalAndResult.semaphore.acquire();
            if(!signalAndResult.finish){
                signalAndResult.semaphore.acquire();
            }

            assert signalAndResult.finish;
            //if(signalAndResult.finish){

            //}

        }catch (InterruptedException ex){
            ex.printStackTrace();
        }

        /*
        for(Case<T> _case:shuffle){
            _case.channel.lock();
        }
        */

        /*
        for(ReentrantLock r:lockOrder){
            r.lock();
        }
        */
        for(SemaphoreChannel<T> channel:lockOrder){
            channel.lock.lock();
        }


        try {
            for (Case<T> _case:shuffle){
                if(_case.sendData!=null){

                    _case.channel.deregisterSender(_case.node);
                }else{

                    _case.channel.deregisterReceiver(_case.node);
                }
            }
        }finally {
            /*
            for(Case<T> _case:shuffle){
                _case.channel.unlock();
            }
            */

            /*
            for(ReentrantLock r:lockOrder){
                r.unlock();
            }
            */

            for(SemaphoreChannel<T> channel:lockOrder){
                channel.lock.unlock();
            }


        }

        //这个要不要放到锁里读
        return new SelectResult<>(signalAndResult.index,signalAndResult.channel,signalAndResult.result);
    }

    public static class SelectResult<T>{
        public int index;
        public SemaphoreChannel<T> channel;
        public T result;

        public SelectResult(int index, SemaphoreChannel<T> channel, T result) {
            this.index = index;
            this.channel = channel;
            this.result = result;
        }
    }
}

//data message result value