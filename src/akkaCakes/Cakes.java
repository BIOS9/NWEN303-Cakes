package akkaCakes;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import akka.actor.*;
import akka.pattern.Patterns;
import akkaUtils.AkkaConfig;
import dataCakes.Cake;
import dataCakes.Gift;
import dataCakes.Sugar;
import dataCakes.Wheat;

@SuppressWarnings("serial")
class GiftRequest implements Serializable {
}

@SuppressWarnings("serial")
class GiveOne implements Serializable {
}

@SuppressWarnings("serial")
class MakeOne implements Serializable {
}

abstract class Producer<T> extends AbstractActor {
    public final Class<T> genericType;
    public final int maxProducts;
    private boolean running;
    private Queue<T> products = new LinkedList<>();

    protected abstract CompletableFuture<T> make();

    public Producer(Class<T> genericType, int maxProducts) {
        this.genericType = genericType;
        this.maxProducts = maxProducts;
    }

    public Receive createReceive() {
        return receiveBuilder()
                .match(genericType, r -> {
                    products.offer(r);
                })
                .match(MakeOne.class, r -> {
                    if(products.size() >= maxProducts) { // Products is full
                        running = false;
                    } else { // Products is not full
                        CompletableFuture<T> makeFuture = make();
                        CompletableFuture<MakeOne> makeComplete = makeFuture.thenApply((p) -> new MakeOne());

                        Patterns.pipe(makeFuture, getContext().dispatcher()).to(self());
                        Patterns.pipe(makeComplete, getContext().dispatcher()).to(self());
                    }
                })
                .match(GiveOne.class, r -> {
                    if(products.isEmpty()) { // Products is empty
                        ActorRef sender = sender();
                        Patterns.pipe(make(), getContext().dispatcher()).to(sender);
                    } else { // Products is not empty
                        sender().tell(products.poll(), self());
                    }

                    if(!running && products.size() < maxProducts) {
                        running = true;
                        self().tell(new MakeOne(), sender());
                    }
                })
                .build();
    }
}

//--------
class Alice extends Producer<Wheat> {
    public Alice(int maxProducts) {
        super(Wheat.class, maxProducts);
    }

    @Override
    protected CompletableFuture<Wheat> make() {
        return CompletableFuture.supplyAsync(() -> new Wheat());
    }
}

class Bob extends Producer<Sugar> {
    public Bob(int maxProducts) {
        super(Sugar.class, maxProducts);
    }

    @Override
    protected CompletableFuture<Sugar> make() {
        return CompletableFuture.supplyAsync(() -> new Sugar());
    }
}

class Charles extends Producer<Cake> {
    final ActorRef alice;
    final ActorRef[] bobs;
    int bobIndex = 0;

    public Charles(int maxProducts, ActorRef alice, ActorRef[] bobs) {
        super(Cake.class, maxProducts);
        this.alice = alice;
        this.bobs = bobs;
    }

    @Override
    protected CompletableFuture<Cake> make() {
        CompletableFuture<Object> wheat = Patterns.ask(alice, new GiveOne(), Duration.ofMillis(10_000_000)).toCompletableFuture();

        int index = bobIndex++;
        if(bobIndex >= bobs.length)
            bobIndex = 0;

        CompletableFuture<Object> sugar = Patterns.ask(bobs[index], new GiveOne(), Duration.ofMillis(10_000_000)).toCompletableFuture();
        return wheat.thenCombine(sugar, (w, s) -> new Cake((Sugar)s, (Wheat)w));
    }
}

class Tim extends AbstractActor {
    int hunger;

    public Tim(int hunger, ActorRef charles) {
        this.hunger = hunger;
        this.charles = charles;
    }

    boolean running = true;
    ActorRef originalSender = null;
    ActorRef charles;

    public Receive createReceive() {
        return receiveBuilder()
                .match(GiftRequest.class, () -> originalSender == null, gr -> {
                    originalSender = sender();
                    charles.tell(new GiveOne(), self());
                })
                .match(Cake.class, () -> running, c -> {
                    hunger -= 1;
                    System.out.println("JUMMY but I'm still hungry " + hunger);
                    if (hunger > 0) {
                        charles.tell(new GiveOne(), self());
                        return;
                    }
                    running = false;
                    originalSender.tell(new Gift(), self());
                })
                .build();
    }
}

public class Cakes {
    public static void main(String[] args) {
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
        Gift g = computeGift(1000, 5);
        assert g != null;
        System.out.println(
                "\n\n-----------------------------\n\n" +
                        g +
                        "\n\n-----------------------------\n\n");
    }

    public static Gift computeGift(int hunger, int maxProducts) {
        ActorSystem s = AkkaConfig.newSystem("Cakes", 2501,

//                Collections.emptyMap()

            AkkaConfig.makeMap(
                "Bob0", "172.17.0.142:2300",
                    "Bob1", "172.17.0.142:2301",
                    "Bob2", "172.17.0.142:2302",
                    "Bob3", "172.17.0.142:2303"
            )
        );

        ActorRef alice =//makes wheat
                s.actorOf(Props.create(Alice.class, () -> new Alice(maxProducts)), "Alice");

        ActorRef[] bobs = new ActorRef[4];
        for(int i = 0; i < bobs.length; ++i) {
            bobs[i] = s.actorOf(Props.create(Bob.class, () -> new Bob(maxProducts)), "Bob" + i);
        }

        ActorRef charles =// makes cakes with wheat and sugar
                s.actorOf(Props.create(Charles.class, () -> new Charles(maxProducts, alice, bobs)), "Charles");
        ActorRef tim =//tim wants to eat cakes
                s.actorOf(Props.create(Tim.class, () -> new Tim(hunger, charles)), "Tim");

        long startMillis = System.currentTimeMillis();
        CompletableFuture<Object> gift = Patterns.ask(tim, new GiftRequest(), Duration.ofMillis(10_000_000)).toCompletableFuture();
        gift.thenAccept((g) -> {
           long millis = System.currentTimeMillis() - startMillis;
           System.out.println("Took: " + millis + "ms to get gift.");
        });
        try {
            return (Gift) gift.join();
        } finally {
            alice.tell(PoisonPill.getInstance(), ActorRef.noSender());
            for(ActorRef bob : bobs) {
                bob.tell(PoisonPill.getInstance(), ActorRef.noSender());
            }
            charles.tell(PoisonPill.getInstance(), ActorRef.noSender());
            tim.tell(PoisonPill.getInstance(), ActorRef.noSender());
            s.terminate();
        }
    }
}