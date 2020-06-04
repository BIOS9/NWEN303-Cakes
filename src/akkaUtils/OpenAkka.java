package akkaUtils;

import java.util.Collections;
import akka.actor.ActorSystem;
public class OpenAkka{
  public static void main(String[]args) throws InterruptedException {
    int port = 2500;
    if(args.length >= 1) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (Exception e) {}
    }
    ActorSystem s = AkkaConfig.newSystem("OpenAkka", port, Collections.emptyMap());
    System.out.println("Akka running on port: " + port);
    AkkaConfig.keybordClose(s);
  }
}
