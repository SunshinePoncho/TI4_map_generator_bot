package ti4.helpers;

import java.util.concurrent.ThreadLocalRandom;

public class RandomHelper {
    public static boolean isOneInX(int x) {
        return ThreadLocalRandom.current().nextInt(x) == 0;
    }
}
