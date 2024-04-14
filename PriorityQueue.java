import java.util.Iterator;
import java.util.LinkedList;

public class PriorityQueue<E extends Comparable<? super E>>{
    // Instance variables
    private LinkedList<E> list;
    private int size;

    public PriorityQueue() {
        this.list = new LinkedList<>();
        this.size = 0;
    }

    public boolean enqueue(E data) {
        // If the list is empty it does not matter where we add
        if (size == 0) {
            list.add(data);
            size++;
            return true;
        }

        int index = 0;
        Iterator<E> i = list.iterator();
        while (i.hasNext() && i.next().compareTo(data) <= 0) {
            index++;
        }
        list.add(index, data);
        size++;
        return true;
    }

    public E dequeue() {
        // Return null if list is empty
        if (size == 0) {
            return null;
        }
        this.size--;
        return list.removeFirst();

    }

    public int size() {
        return this.size;
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
