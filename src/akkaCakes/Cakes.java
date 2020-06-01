package akkaCakes;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.Patterns;
import akkaUtils.AkkaConfig;
import dataCakes.Cake;
import dataCakes.Gift;
import dataCakes.Sugar;
import dataCakes.Wheat;

@SuppressWarnings("serial")
class GiftRequest implements Serializable {
}

//--------
class Alice extends AbstractActor {
    public Receive createReceive() {
        return receiveBuilder()
                .match(ActorRef.class, r -> {//startup message
                    r.tell(new Wheat(), sender());
                    self().tell(r, sender());
                })
                .build();
    }
}

class Bob extends AbstractActor {
    public Receive createReceive() {
        return receiveBuilder()
                .match(ActorRef.class, r -> {
                    r.tell(new Sugar(), sender());
                    self().tell(r, sender());
                })
                .build();
    }
}

class Charles extends AbstractActor {
    List<Wheat> ws = new ArrayList<>();//no synchronization issues!
    List<Sugar> ss = new ArrayList<>();//no synchronization issues!

    public Receive createReceive() {
        return receiveBuilder()
                .match(Wheat.class, w -> {
                    if (ss.isEmpty()) {
                        ws.add(w);
                        return;
                    }
                    Sugar s = ss.remove(ss.size() - 1);
                    //ActorSelection tim =context().actorSelection("akka://Cakes/user/Tim");
                    sender().tell(new Cake(s, w), self());
                })
                .match(Sugar.class, s -> {
                    if (ws.isEmpty()) {
                        ss.add(s);
                        return;
                    }
                    Wheat w = ws.remove(ws.size() - 1);
                    sender().tell(new Cake(s, w), self());
                })
                .build();
    }
}

class Tim extends AbstractActor {
    int hunger;

    public Tim(int hunger) {
        this.hunger = hunger;
    }

    boolean running = true;
    ActorRef originalSender = null;

    public Receive createReceive() {
        return receiveBuilder()
                .match(GiftRequest.class, () -> originalSender == null, gr -> {
                    originalSender = sender();
                })
                .match(Cake.class, () -> running, c -> {
                    hunger -= 1;
                    System.out.println("JUMMY but I'm still hungry " + hunger);
                    if (hunger > 0) {
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
        Gift g = computeGift(1000);
        assert g != null;
        System.out.println(
                "\n\n-----------------------------\n\n" +
                        g +
                        "\n\n-----------------------------\n\n");
    }

    public static Gift computeGift(int hunger) {
        ActorSystem s = AkkaConfig.newSystem("Cakes", 2501,/*Collections.emptyMap()*/AkkaConfig.makeMap(
                "Tim", "192.168.56.1",
                "Bob", "192.168.56.1",
                "Charles", "192.168.56.1"
                //Alice stays local
        ));
        ActorRef alice =//makes wheat
                s.actorOf(Props.create(Alice.class, () -> new Alice()), "Alice");
        ActorRef bob =//makes sugar
                s.actorOf(Props.create(Bob.class, () -> new Bob()), "Bob");
        ActorRef charles =// makes cakes with wheat and sugar
                s.actorOf(Props.create(Charles.class, () -> new Charles()), "Charles");
        ActorRef tim =//tim wants to eat cakes
                s.actorOf(Props.create(Tim.class, () -> new Tim(hunger)), "Tim");
        alice.tell(charles, tim);
        bob.tell(charles, tim);
        CompletableFuture<Object> gift = Patterns.ask(tim, new GiftRequest(), Duration.ofMillis(10_000_000)).toCompletableFuture();
        try {
            return (Gift) gift.join();
        } finally {
            alice.tell(PoisonPill.getInstance(), ActorRef.noSender());
            bob.tell(PoisonPill.getInstance(), ActorRef.noSender());
            charles.tell(PoisonPill.getInstance(), ActorRef.noSender());
            tim.tell(PoisonPill.getInstance(), ActorRef.noSender());
            s.terminate();
        }
    }
}