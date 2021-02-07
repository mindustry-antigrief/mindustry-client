package client;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.client.Client;
import mindustry.client.utils.FloatEmbed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FloatEmbedTests {

    /** Tests the float embedding system. */
    @Test
    void testFloatEmbed() {
        byte[] temp = new byte[1];
        Mathf.rand.nextBytes(temp);
        byte item = temp[0];

        float input = (float) (Mathf.rand.nextDouble() * 10000);

        float embedded = FloatEmbed.embedInFloat(input, item);
        Assertions.assertTrue(Math.abs(embedded - input) < 0.1);
        Assertions.assertTrue(FloatEmbed.isEmbedded(embedded, item));

        Vec2 inputVector = new Vec2((float) (Mathf.rand.nextDouble() * 10000), (float) (Mathf.rand.nextDouble() * 10000));
        Vec2 notAssisting = new Vec2(FloatEmbed.embedInFloat(inputVector.x, Client.FOO_USER), FloatEmbed.embedInFloat(inputVector.y, Client.FOO_USER));
        Vec2 assisting = new Vec2(FloatEmbed.embedInFloat(inputVector.x, Client.FOO_USER), FloatEmbed.embedInFloat(inputVector.y, Client.ASSISTING));

        Assertions.assertTrue(notAssisting.dst(inputVector) < 0.1f);
        Assertions.assertTrue(assisting.dst(inputVector) < 0.1f);

        Assertions.assertTrue(FloatEmbed.isEmbedded(notAssisting.x, Client.FOO_USER));
        Assertions.assertTrue(FloatEmbed.isEmbedded(assisting.x, Client.FOO_USER));

        Assertions.assertFalse(FloatEmbed.isEmbedded(notAssisting.y, Client.ASSISTING));
        Assertions.assertTrue(FloatEmbed.isEmbedded(assisting.y, Client.ASSISTING));
    }
}
