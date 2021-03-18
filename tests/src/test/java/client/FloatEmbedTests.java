package client;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import com.github.blahblahbloopster.ClientVarsImpl;
import mindustry.client.Client;
import mindustry.client.utils.FloatEmbed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FloatEmbedTests {

    /** Tests the float embedding system. */
    @Test
    void testFloatEmbed() {
        Client.vars = ClientVarsImpl.INSTANCE;
        byte[] temp = new byte[1];
        Mathf.rand.nextBytes(temp);
        byte item = temp[0];

        float input = (float) (Mathf.rand.nextDouble() * 8000);

        float embedded = FloatEmbed.embedInFloat(input, item);
        Assertions.assertTrue(Math.abs(embedded - input) < 1f);
        Assertions.assertTrue(FloatEmbed.isEmbedded(embedded, item));

        Vec2 inputVector = new Vec2((float) (Mathf.rand.nextDouble() * 8000), (float) (Mathf.rand.nextDouble() * 8000));
        Vec2 notAssisting = new Vec2(FloatEmbed.embedInFloat(inputVector.x, Client.vars.getFooUser()), FloatEmbed.embedInFloat(inputVector.y, Client.vars.getFooUser()));
        Vec2 assisting = new Vec2(FloatEmbed.embedInFloat(inputVector.x, Client.vars.getFooUser()), FloatEmbed.embedInFloat(inputVector.y, Client.vars.getAssisting()));

        Assertions.assertTrue(notAssisting.dst(inputVector) < 1f);
        Assertions.assertTrue(assisting.dst(inputVector) < 1f);

        Assertions.assertTrue(FloatEmbed.isEmbedded(notAssisting.x, Client.vars.getFooUser()));
        Assertions.assertTrue(FloatEmbed.isEmbedded(assisting.x, Client.vars.getFooUser()));

        Assertions.assertFalse(FloatEmbed.isEmbedded(notAssisting.y, Client.vars.getAssisting()));
        Assertions.assertTrue(FloatEmbed.isEmbedded(assisting.y, Client.vars.getAssisting()));
    }
}
