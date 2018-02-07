import com.launchdarkly.client.LDClient;
import com.launchdarkly.client.LDUser;

import java.io.IOException;

import static java.util.Collections.singletonList;

public class Hello {

 public static void main(String... args) throws IOException {
   LDClient client = new LDClient("sdk-03947004-7d32-4878-a80b-ade2314efece");

   LDUser user = new LDUser.Builder("bob@example.com")
                           .firstName("Bob")
                           .lastName("Loblaw")
                           .customString("groups", singletonList("beta_testers"))
                           .build();

   boolean showFeature = client.boolVariation("new.dashboard", user, false);

   if (showFeature) {
    System.out.println("Showing your feature");
   } else {
    System.out.println("Not showing your feature");
   }

   client.flush();
   client.close();
   
   System.out.println("bye bye");
 }
}