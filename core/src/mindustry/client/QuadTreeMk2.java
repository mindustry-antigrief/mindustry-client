package mindustry.client;

import arc.func.*;
import arc.math.geom.*;
import arc.struct.*;

import java.util.*;

public class QuadTreeMk2<T extends QuadTree.QuadTreeObject> extends QuadTree<T>{
    public int totalObjects;
    private Seq<QuadTree<T>> stack;

    public QuadTreeMk2(Rect bounds){
        super(bounds);
    }

    private void split(){
        if(!leaf) return;

        float subW = bounds.width / 2;
        float subH = bounds.height / 2;

        if(botLeft == null){
            botLeft = newChild(new Rect(bounds.x, bounds.y, subW, subH));
            botRight = newChild(new Rect(bounds.x + subW, bounds.y, subW, subH));
            topLeft = newChild(new Rect(bounds.x, bounds.y + subH, subW, subH));
            topRight = newChild(new Rect(bounds.x + subW, bounds.y + subH, subW, subH));
        }
        leaf = false;

        // Transfer objects to children if they fit entirely in one
        for(Iterator<T> iterator = objects.iterator(); iterator.hasNext();){
            T obj = iterator.next();
            hitbox(obj);
            QuadTree<T> child = getFittingChild(tmp);
            if(child != null){
                child.insert(obj);
                iterator.remove();
            }
        }
    }

    private void unsplit(){
        if(leaf) return;
        objects.addAll(botLeft.objects);
        objects.addAll(botRight.objects);
        objects.addAll(topLeft.objects);
        objects.addAll(topRight.objects);
        botLeft.clear();
        botRight.clear();
        topLeft.clear();
        topRight.clear();
        leaf = true;
    }

    /**
     * Inserts an object into this node or its child nodes. This will split a leaf node if it exceeds the object limit.
     */
    @Override
    public void insert(T obj){
        hitbox(obj);
        if(!bounds.overlaps(tmp)){
            // New object not in quad tree, ignoring
            // throw an exception?
            return;
        }

        totalObjects ++;

        if(leaf && objects.size + 1 > maxObjectsPerNode) split();

        if(leaf){
            // Leaf, so no need to add to children, just add to root
            objects.add(obj);
        }else{
            hitbox(obj);
            // Add to relevant child, or root if can't fit completely in a child
            QuadTreeMk2<T> child = (QuadTreeMk2<T>)getFittingChild(tmp);
            if(child != null){
                child.insert(obj);
            }else{
                objects.add(obj);
            }
        }
    }

    /**
     * Removes an object from this node or its child nodes.
     */
    @Override
    public void remove(T obj){
        removeB(obj);
    }

    // Can't have different return type
    private boolean removeB(T obj){
        boolean result;
        if(leaf){ // Leaf, no children, remove from root
            result = objects.remove(obj, true);
        }else{ // Remove from relevant child
            hitbox(obj);
            QuadTreeMk2 child = (QuadTreeMk2)getFittingChild(tmp);

            if(child != null){
                result = child.removeB(obj);
            }else{ // Or root if object doesn't fit in a child
                result = objects.remove(obj, true);
            }

            if(totalObjects <= maxObjectsPerNode) unsplit();
        }
        if(result){
            totalObjects --;
        }
        return result;
    }

    /** Removes all objects. */
    @Override
    public void clear(){
        objects.clear();
        totalObjects = 0;
        if(!leaf){
            topLeft.clear();
            topRight.clear();
            botLeft.clear();
            botRight.clear();
        }
        leaf = true;
    }

    private QuadTree<T> getFittingChild(Rect boundingBox){
        float verticalMidpoint = bounds.x + (bounds.width / 2);
        float horizontalMidpoint = bounds.y + (bounds.height / 2);

        // Object can completely fit within the top quadrants
        boolean topQuadrant = boundingBox.y > horizontalMidpoint;
        // Object can completely fit within the bottom quadrants
        boolean bottomQuadrant = boundingBox.y < horizontalMidpoint && (boundingBox.y + boundingBox.height) < horizontalMidpoint;

        // Object can completely fit within the left quadrants
        if(boundingBox.x < verticalMidpoint && boundingBox.x + boundingBox.width < verticalMidpoint){
            if(topQuadrant){
                return topLeft;
            }else if(bottomQuadrant){
                return botLeft;
            }
        }else if(boundingBox.x > verticalMidpoint){ // Object can completely fit within the right quadrants
            if(topQuadrant){
                return topRight;
            }else if(bottomQuadrant){
                return botRight;
            }
        }

        // Else, object needs to be in parent cause it can't fit completely in a quadrant
        return null;
    }

    @Override
    protected QuadTree<T> newChild(Rect rect){
        return new QuadTreeMk2<>(rect);
    }

    @Override
    public void intersect(float x, float y, float width, float height, Cons<T> out) {
        if(stack == null) stack = new Seq<>();
        stack.add(this);

        while(stack.size > 0){
            QuadTreeMk2<T> curr = (QuadTreeMk2<T>)stack.pop();
            if(!curr.leaf){
                if(curr.topLeft.bounds.overlaps(x, y, width, height)) stack.add(curr.topLeft);
                if(curr.topRight.bounds.overlaps(x, y, width, height)) stack.add(curr.topRight);
                if(curr.botLeft.bounds.overlaps(x, y, width, height)) stack.add(curr.botLeft);
                if(curr.botRight.bounds.overlaps(x, y, width, height)) stack.add(curr.botRight);
            }

            Seq<?> objects = curr.objects;

            for(int i = 0; i < objects.size; i++){
                T item = (T)objects.items[i];
                curr.hitbox(item);
                if(curr.tmp.overlaps(x, y, width, height)){
                    out.get(item);
                }
            }
        }
    }

    @Override
    public void intersect(float x, float y, float width, float height, Seq<T> out) {
        if(stack == null) stack = new Seq<>();
        stack.add(this);

        while(stack.size > 0){
            QuadTreeMk2<T> curr = (QuadTreeMk2<T>)stack.pop();
            if(!curr.leaf){
                if(curr.topLeft.bounds.overlaps(x, y, width, height)) stack.add(curr.topLeft);
                if(curr.topRight.bounds.overlaps(x, y, width, height)) stack.add(curr.topRight);
                if(curr.botLeft.bounds.overlaps(x, y, width, height)) stack.add(curr.botLeft);
                if(curr.botRight.bounds.overlaps(x, y, width, height)) stack.add(curr.botRight);
            }

            Seq<?> objects = curr.objects;

            for(int i = 0; i < objects.size; i++){
                T item = (T)objects.items[i];
                curr.hitbox(item);
                if(curr.tmp.overlaps(x, y, width, height)){
                    out.add(item);
                }
            }
        }
    }
}