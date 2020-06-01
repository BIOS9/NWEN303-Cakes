package akkaUtils;

import java.util.Collections;
import akka.actor.ActorSystem;
public class OpenAkka{
  public static void main(String[]args) throws InterruptedException {
    ActorSystem s = AkkaConfig.newSystem("OpenAkka",Integer.parseInt(args[0]),Collections.emptyMap());
    System.out.println("Akka running on port: " + args[0]);
    AkkaConfig.keybordClose(s);
  }
}
