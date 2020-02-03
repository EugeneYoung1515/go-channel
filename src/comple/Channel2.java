package comple;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Channel2<T> {
    
    ReentrantLock lock = new ReentrantLock();

    private Queue<Node<T>> waitingPuts = new LinkedList<>();
    private Queue<Node<T>> waitingTakes = new LinkedList<>();

    private RingBuffer<T> buffer;
    boolean isClosed;

    private static AtomicInteger atomicInteger = new AtomicInteger(0);
    int id;

    static class Node<T> {
        public T value;

        public SignalAndResult<T> signalAndResult;

        public int index;

        public Node() {
            signalAndResult = new SignalAndResult<>();
        }

        public Node(T value, SignalAndResult<T> signalAndResult) {
            this.value = value;
            this.signalAndResult = signalAndResult;
        }

        public Node(SignalAndResult<T> signalAndResult) {
            this.signalAndResult = signalAndResult;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "value=" + value +
                    ", signalAndResult=" + signalAndResult +
                    '}';
        }
    }

    static class SignalAndResult<T>{
        public ReentrantLock lock = new ReentrantLock();
        public Condition condition = lock.newCondition();
        
        public boolean finish;
        public boolean close;

        public Channel2<T> channel;

        public T result;

        public int index;
    }

     public static class RingBuffer<T>{
        T[] items;
        int putptr, takeptr, count;

        public RingBuffer(int buf) {
            items = (T[])new Object[buf];
        }

        public boolean isFull(){
            return count==items.length;
        }

        public boolean isEmpty(){
            return count==0;
        }

        public void put(T x){
            items[putptr] = x;
            if (++putptr == items.length) putptr = 0;
            ++count;
        }

        public T take(){
            T x = items[takeptr];
            if (++takeptr == items.length) takeptr = 0;
            --count;
            return x;
        }
    }

    public Channel2(int buf) {
        buffer = new RingBuffer<>(buf);

        id = atomicInteger.incrementAndGet();
    }

    public void send(T x){
        put(x,false);
    }

     boolean put(T x,boolean nonBlocking){
        if (x == null) throw new IllegalArgumentException();

        while (true){

            Node<T> slot;
            Node<T> item = null;

            lock.lock();

            try {

                if (isClosed){
                    throw new RuntimeException("closed");
                }

                slot = waitingTakes.poll();

                if(slot==null){//没有等待的消费者
                    if(!buffer.isFull()){//buffer没满
                        buffer.put(x);

                        if(Constant.debug){
                            System.out.println(this+" "+Thread.currentThread().getName()+" buffer put: "+x);
                            System.out.println(this+" "+Thread.currentThread().getName()+" buffer 里多少个: "+buffer.count);
                        }

                        return true;//这个return是有用的
                    }else{
                        if(!nonBlocking){
                            waitingPuts.offer(item = new Node<>(x,new SignalAndResult<>()));//生产者进入等待
                        }else{

                            if(Constant.debug){
                                System.out.println(this+" "+Thread.currentThread().getName()+": null 非阻塞put");
                            }

                             return false;//这个return是有用的
                        }
                    }
                }

                if(Constant.debug){
                    //if(Thread.currentThread().getName().contains("0")){
                    System.out.println(this+" "+Thread.currentThread().getName()+" put: 取出的消费者"+" "+slot);
                    System.out.println(this+" "+Thread.currentThread().getName()+" put: 自己"+" "+item);

                    //}
                }

            }finally {
                lock.unlock();
            }

            //这里关闭

            if (slot != null) {
                SignalAndResult<T> sar = slot.signalAndResult;

                sar.lock.lock();
                try {
                    if(!sar.finish){//关闭那里要不要也加一个这样的判断
                        sar.result = x;
                        sar.channel = this;
                        sar.index = slot.index;
                        sar.finish = true;
                        sar.condition.signal();
                        return true;//这个return是有用的
                    }

                    if(Constant.debug){
                        System.out.println(this+" "+Thread.currentThread().getName()+" 已经有其他线程对这个select设置成功");
                    }

                }finally {
                    sar.lock.unlock();
                }
            }else{
                SignalAndResult<T> sar = item.signalAndResult;
                sar.lock.lock();
                try {
                    while (!sar.finish) {
                        sar.condition.await();
                    }


                    if (sar.close){
                        throw new RuntimeException("close");
                    }

                    return false;//这个return是没用的
                    //要不要改成return true

                    //非阻塞的put 不会运行到这里 这里的return只是为了终止循环

                }catch(InterruptedException ie) {
                    ie.printStackTrace();
                }finally {
                    sar.lock.unlock();
                }
            }
            //return false;//这个return是没用的

        }
    }

    public T receive(){
        return take(false);
    }

     T take(boolean nonBlocking){
        while (true){
            Node<T> item = null;
            Node<T> slot = null;

            lock.lock();
            try {

                if(!buffer.isEmpty()){//buffer没空
                    T xx= buffer.take();

                    if(Constant.debug){
                        System.out.println(this+" "+Thread.currentThread().getName()+" buffer take: "+xx);
                        System.out.println(this+" "+Thread.currentThread().getName()+" buffer 里多少个: "+buffer.count);
                    }

                    while (true){
                        item = waitingPuts.poll();
                        if (item != null) {//有等待的生产者

                            SignalAndResult<T> sar = item.signalAndResult;

                            sar.lock.lock();
                            try {
                                if(!sar.finish){
                                    T x = item.value;
                                    item.value = null;
                                    sar.channel = this;
                                    sar.index = item.index;
                                    sar.finish = true;
                                    sar.condition.signal();

                                    if(Constant.debug){
                                        System.out.println(this+" "+Thread.currentThread().getName()+" "+"把一个等待的生产者的值放到buffer中: "+x);
                                    }

                                    buffer.put(x);//要把一个等待的生产者的值放到buffer中
                                    break;//?
                                }

                                if(Constant.debug){
                                    System.out.println(this+" "+Thread.currentThread().getName()+" 尝试把一个等待的生产者的值放到buffer中 但是 已经有其他线程对这个select设置成功");
                                }

                            }finally {
                                sar.lock.unlock();
                            }
                        }else{
                            break;
                        }
                    }

                    return xx;
                }else{
                    item = waitingPuts.poll();
                    if(item==null) {

                        //这里关闭？
                        if (isClosed){
                            return null;
                        }

                        if(!nonBlocking) {
                            waitingTakes.offer(slot = new Node<>());
                        }else{
                            if(Constant.debug){
                                System.out.println(this+" "+Thread.currentThread().getName()+": null 非阻塞take");
                            }
                            return null;
                        }

                        //上面的 关闭 和非阻塞 放的位置和逻辑
                    }

                    if(Constant.debug){
                        //if(Thread.currentThread().getName().contains("1")){
                        System.out.println(this+" "+Thread.currentThread().getName()+" take: 取出的生产者"+" "+item);
                        System.out.println(this+" "+Thread.currentThread().getName()+" take: 自己"+" "+slot);
                        //}
                    }
                }

            }finally {
                lock.unlock();
            }

            //这里关闭

            if (item != null) {
                SignalAndResult<T> sar = item.signalAndResult;
                sar.lock.lock();
                try {
                    if(!sar.finish){
                        T x = item.value;
                        item.value = null;
                        sar.channel = this;
                        sar.index = item.index;
                        sar.finish = true;
                        sar.condition.signal();
                        return x;
                    }

                    //System.out.println("ffff");

                    if(Constant.debug){
                        System.out.println(this+" "+Thread.currentThread().getName()+" 已经有其他线程对这个select设置成功");
                    }

                }finally {
                    sar.lock.unlock();
                }
            }else{
                SignalAndResult<T> sar = slot.signalAndResult;
                sar.lock.lock();
                try {
                    for (;;) {
                        T x = sar.result;
                        if (sar.finish) {
                            sar.result = null;
                            return x;
                        }else {
                            sar.condition.await();
                        }
                    }
                }catch(InterruptedException ie) {
                    ie.printStackTrace();
                }finally {
                    sar.lock.unlock();
                }
            }
            //return null;
        }
    }


    public void close(){
        List<Node<T>> all = new ArrayList<>(10);

        lock.lock();
        try {
            if(!isClosed){
                isClosed = true;
            }else{
                throw new RuntimeException("double closed");
            }

            all.addAll(waitingPuts);
            all.addAll(waitingTakes);

            waitingPuts = new LinkedList<>();
            waitingTakes = new LinkedList<>();
        }finally {
            lock.unlock();
        }

        for (Node<T> node : all) {//这个要不要向select那里一样 现全部上锁 最后是全部解锁
                                    //这里是上锁一个处理一个
            
            SignalAndResult<T> sar = node.signalAndResult;
            
            sar.lock.lock();
            sar.close = true;
            sar.finish = true;
            sar.condition.signal();
            sar.lock.unlock();
        }
    }

    void lock(){
        lock.lock();
    }

    void unlock(){
        lock.unlock();
    }

    void registerSender(Node<T> node){
        waitingPuts.offer(node);
        if(Constant.debug){
            System.out.println(this+" "+Thread.currentThread().getName()+" sender regi:"+" "+node);
        }
    }

    void registerReceiver(Node<T> node){
        waitingTakes.offer(node);

        if(Constant.debug){
            //if(Thread.currentThread().getName().contains("1")){
            System.out.println(this+" "+Thread.currentThread().getName()+" receive regi:"+" "+node);
            //}
        }
    }

    void deregisterSender(Node<T> node){
        waitingPuts.remove(node);
    }

    void deregisterReceiver(Node<T> node){
        waitingTakes.remove(node);
    }

    public int getBufLength(){
        return buffer.count;
    }

    public RingBuffer getBuf(){
        return buffer;
    }

    public int getId() {
        return id;
    }
}