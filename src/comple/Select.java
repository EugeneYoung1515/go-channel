package comple;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Select<T> {
    private int i;

    private List<Case<T>> list = new ArrayList<>(10);

    private static class Case<T>{
        private Channel2<T> channel;
        private T sendData;
        private int index;

        private Channel2.Node<T> node;

        public Case(Channel2<T> channel, T sendData) {
            this.channel = channel;
            this.sendData = sendData;
        }

        public Case(Channel2<T> channel) {
            this.channel = channel;
        }

        public Case(Channel2<T> channel, T sendData, int index) {
            this.channel = channel;
            this.sendData = sendData;
            this.index = index;
        }

        public Case(Channel2<T> channel, int index) {
            this.channel = channel;
            this.index = index;
        }
    }

    public Select<T> send(Channel2<T> channel,T sendData){
        list.add(new Case<>(channel,sendData,i));
        i++;
        return this;
    }

    public Select<T> receive(Channel2<T> channel){
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
        Channel2.SignalAndResult<T> signalAndResult = new Channel2.SignalAndResult<>();

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
        Set<Channel2<T>> lockOrder = new TreeSet<>(new Comparator<Channel2<T>>() {
            @Override
            public int compare(Channel2<T> o1, Channel2<T> o2) {
                return o1.id-o2.id;
            }
        });
        for (Case<T> _case:shuffle){
            lockOrder.add(_case.channel);
        }

        for(Channel2<T> channel:lockOrder){
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
                    Channel2.Node<T> node = new Channel2.Node<>(_case.sendData,signalAndResult);
                    node.index = _case.index;

                    _case.node = node;//作用 下面deregisterSender是用

                    _case.channel.registerSender(node);
                }else{
                    Channel2.Node<T> node = new Channel2.Node<>(signalAndResult);
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

            for(Channel2<T> channel:lockOrder){
                channel.lock.unlock();
            }


        }


        signalAndResult.lock.lock();
        try {
            while (!signalAndResult.finish){
                signalAndResult.condition.await();
            }

        }catch (InterruptedException ex){
            ex.printStackTrace();
        }finally {
            signalAndResult.lock.unlock();
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

        for(Channel2<T> channel:lockOrder){
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

            for(Channel2<T> channel:lockOrder){
                channel.lock.unlock();
            }


        }

        //这个要不要放到锁里读
        return new SelectResult<>(signalAndResult.index,signalAndResult.channel,signalAndResult.result);
    }

    public static class SelectResult<T>{
        public int index;
        public Channel2<T> channel;
        public T result;

        public SelectResult(int index, Channel2<T> channel, T result) {
            this.index = index;
            this.channel = channel;
            this.result = result;
        }
    }
}

//data message result value