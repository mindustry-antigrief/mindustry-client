package mindustry.client.utils;

/**
 * Does exactly what you think, why isn't this built in
 */
public class Pair<K, V> {

    public Object first, second;

    public Pair(K first, V second) {
        this.first = first;
        this.second = second;
    }
}
