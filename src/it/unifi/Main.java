package it.unifi;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int N = 3;  //Requester
        int M = 6;   //Worker
        int K = 12;  //Assigner
        int R = 100;  //Limite Queue
        Requester[] requesters = new Requester[N];
        Assigner[] assigners = new Assigner[K];
        Worker[] workers = new Worker[M];
        Counter counter = new Counter();
        Queue queue = new Queue(R);
        ReqDispatcher reqDispatcher1 = new ReqDispatcher(M);
        ReqDispatcher reqDispatcher2 = new ReqDispatcher(N);
        for (int i = 0; i < requesters.length; i++) {
            requesters[i] = new Requester(i, queue, counter, reqDispatcher2, N);
            requesters[i].setName("R" + (i+1));
            requesters[i].start();
        }
        for (int i = 0; i < assigners.length; i++) {
            assigners[i] = new Assigner(queue, M, reqDispatcher1);
            assigners[i].setName("A" + i);
            assigners[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Worker(i, reqDispatcher1, reqDispatcher2, workers);
            workers[i].setName("W" + i);
            workers[i].start();
        }
        Thread.sleep(20000);
        for (Requester r : requesters)
            r.interrupt();
        for (Assigner a : assigners)
            a.interrupt();
        int sum = -M;
        for (Worker w : workers) {
            w.end = true;
            w.interrupt();
            sum += w.restarted;
        }
        System.out.println("Worker stoppati e poi fatti ripartire: " + sum);
        for (int i = 0; i < Assigner.nTimesUsed.length; i++) {
            System.out.println("Worker " + (i + 1) + " assegnato " + (Assigner.nTimesUsed[i]) + " volte");
        }
    }
}

class Requester extends Thread {
    private Queue queue;
    private ReqDispatcher reqDispatcher;
    private Counter counter;
    private int id;
    private long time;


    public Requester(int id, Queue q, Counter c, ReqDispatcher reqDispatcher, int N) {
        this.id = id;
        queue = q;
        counter = c;
        this.reqDispatcher = reqDispatcher;
    }


    @Override
    public void run() {
        try {
            while (true) {
                long start = System.currentTimeMillis();
                int count = counter.getCount();
                queue.put(new Request(id, count));
                int t = (int) (Math.random() * 1000);
                sleep(t);
                int result = reqDispatcher.get(id).val;
                time = System.currentTimeMillis() - start;
                System.out.println(getName() + " Val inviato: " + count + " Val ricevuto: " + result + " in tempo: " + time + "ms");
            }
        } catch (InterruptedException e) {
        }
    }
}

class Assigner extends Thread {
    private Queue queue;
    private ReqDispatcher reqDispatcher;
    public static int[] nTimesUsed;

    public Assigner(Queue q, int M, ReqDispatcher reqDispatcher) {
        queue = q;
        this.reqDispatcher = reqDispatcher;
        nTimesUsed = new int[M];
        for (int i = 0; i < M; i++) {
            nTimesUsed[i] = 0;
        }
    }

    @Override
    public void run() {
        try {
            Request r;
            while (true) {
                r = queue.get();
                int id = getMinUsed();
                reqDispatcher.put(r, id);
                nTimesUsed[id]++;

            }
        } catch (InterruptedException e) {
        }
    }

    public int getMinUsed() {
        int min = Integer.MAX_VALUE;
        int index = -1;
        for (int i = 0; i < nTimesUsed.length; i++) {
            if (nTimesUsed[i] < min) {
                min = nTimesUsed[i];
                index = i;
            }
        }
        return index;
    }
}

class Worker extends Thread {
    private int id;
    ReqDispatcher inReqDispatcher;
    ReqDispatcher outReqDispatcher;
    Worker[] workers;
    static int restarted = 0;
    static boolean end = false;


    public Worker(int id, ReqDispatcher inReqDispatcher, ReqDispatcher outReqDispatcher, Worker[] w) {
        this.id = id;
        this.inReqDispatcher = inReqDispatcher;
        this.outReqDispatcher = outReqDispatcher;
        workers = w;
    }

    @Override
    public void run() {
        try {
            Request r;
            while (true) {
                r = inReqDispatcher.get(this.id);
                int random = (int) (Math.random() * 10);
                if (random == 1) {
                    r.interrupted++;
                    if (r.interrupted == 3) {
                        System.out.println("--------> Persa request con id " + r.id + " e valore " + r.val+" <--------");
                        r = inReqDispatcher.get(this.id);
                        outReqDispatcher.put(new Request(r.id, r.val * 2), r.id);
                    } else {
                        inReqDispatcher.put(r, this.id);
                        this.interrupt();
                    }
                } else {
                    outReqDispatcher.put(new Request(r.id, r.val * 2), r.id);
                }
            }
        } catch (InterruptedException e) {
            if (!end) {
                try {
                    sleep(1000);
                } catch (InterruptedException e2) {
                }
                if (!this.isInterrupted()) {
                    restarted++;
                    Worker w = new Worker(id, inReqDispatcher, outReqDispatcher, workers);
                    workers[this.id] = w;
                    w.setName("W" + id);
                    w.start();
                    Worker tmp = this;
                    System.out.println(getName() + " Stoppato e fatto ripartire");
                    tmp.stop();
                }
            }
        }
    }
}

class Queue {
    private Semaphore mutex = new Semaphore(1, true);
    private Semaphore vuote;
    private Semaphore piene = new Semaphore(0);
    private ArrayList<Request> queue = new ArrayList<>();


    public Queue(int R) {
        vuote = new Semaphore(R);
    }


    public void put(Request r) throws InterruptedException {
        vuote.acquire();
        mutex.acquire();
        queue.add(r);
        mutex.release();
        piene.release();
    }

    public Request get() throws InterruptedException {
        piene.acquire();
        mutex.acquire();
        Request r = queue.remove(0);
        mutex.release();
        vuote.release();
        return r;
    }
}

class ReqDispatcher {
    private Semaphore[] mutex;
    private Semaphore[] piene;
    private Semaphore[] vuote;
    private Request[] req;


    public ReqDispatcher(int n) {
        mutex = new Semaphore[n];
        piene = new Semaphore[n];
        vuote = new Semaphore[n];
        req = new Request[n];
        for (int i = 0; i < mutex.length; i++) {
            mutex[i] = new Semaphore(1);
            piene[i] = new Semaphore(0);
            vuote[i] = new Semaphore(1);
        }
    }

    public void put(Request r, int id) throws InterruptedException {
        vuote[id].acquire();
        mutex[id].acquire();
        req[id] = r;
        mutex[id].release();
        piene[id].release();
    }

    public Request get(int id) throws InterruptedException {
        piene[id].acquire();
        mutex[id].acquire();
        Request r = req[id];
        mutex[id].release();
        vuote[id].release();
        return r;
    }
}

class Request {
    int id;
    int val;
    int interrupted = 0;

    Request(int id, int val) {
        this.id = id;
        this.val = val;
    }
}

class Counter {
    private int count = 0;
    private Semaphore mutex = new Semaphore(1, true);

    public int getCount() throws InterruptedException {
        mutex.acquire();
        int r = count;
        count++;
        mutex.release();
        return r;
    }
}