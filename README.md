# go-channel
一个Java版本的Go Channel及Select，基于Java 5 的SynchronousQueue的前身util.concurrent.SynchronousChannel。

代码骨架是基于Java 5 的SynchronousQueue的前身[util.concurrent.SynchronousChannel](http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/SynchronousChannel.java)，使用条件变量实现叫醒等待的生产者或消费者。Ring Buffer代码拷贝自Condition(条件变量)的[Javadoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/Condition.html)。相似实现：[stuglaser/pychan](https://github.com/stuglaser/pychan)，stuglaser/pychan的Java版本见本人的另一个项目[go-concurrency-to-java](https://github.com/EugeneYoung1515/go-concurrency-to-java/blob/master/go-concurrency-to-java/src/main/java/com/ywcjxf/java/go/concurrent/chan/impl/third/pychan/Chan.java)。

提供另一个版本（SemaphoreChannel和SemaphoreSelect），使用信号量叫醒等待的生产者或消费者。



