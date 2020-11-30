package mindustry.client.navigation;

import arc.math.*;
import arc.struct.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;
import java.util.*;

import static mindustry.Vars.tilesize;


// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified


public class AStar{
    public static final int DIAGONAL_COST = 14;
    public static final int V_H_COST = 10;

    static class Cell{
        int heuristicCost = 0; //Heuristic cost
        int finalCost = 0; //G+H
        int i, j;
        Cell parent;

        Cell(int i, int j){
            this.i = i;
            this.j = j;
        }

        @Override
        public String toString(){
            return "[" + this.i + ", " + this.j + "]";
        }
    }

    //Blocked cells are just null Cell values in grid
    static Cell[][] grid = new Cell[5][5];

    static PriorityQueue<Cell> open = new PriorityQueue<>();

    static boolean[][] closed;
    static boolean[][] costly;
    static int startI, startJ;
    static int endI, endJ;
    static boolean block = false;

    public static void setBlocked(int i, int j){
        grid[i][j] = null;
    }

    public static void setStartCell(int i, int j){
        startI = i;
        startJ = j;
    }

    public static void setEndCell(int i, int j){
        endI = i;
        endJ = j;
    }

    static void checkAndUpdateCost(Cell current, Cell t, int cost){
        if(t == null || closed[t.i][t.j]) return;
        int t_final_cost = t.heuristicCost + cost;
//        if(closed[t.i][t.j]){
//            t_final_cost *= 100;
//        }

        boolean inOpen = open.contains(t);
        if(!inOpen || t_final_cost < t.finalCost){
            t.finalCost = t_final_cost;
            t.parent = current;
            if(!inOpen) open.add(t);
        }
    }

    public static void AStarSearch(){
//        System.out.println(grid.length);
//        System.out.println(grid[0].length);
//        System.out.println(startI);
//        System.out.println(startJ);
//        System.out.println(Arrays.deepToString(grid));
//        System.out.println(Arrays.toString(grid[startI]));
        //add the start location to open list.

        endI = Mathf.clamp(endI, 0, grid.length - 1);
        endJ = Mathf.clamp(endJ, 0, grid[0].length - 1);

        startI = Mathf.clamp(startI, 0, grid.length - 1);
        startJ = Mathf.clamp(startJ, 0, grid[0].length - 1);

        open.add(grid[startI][startJ]);

        Cell current;
//        System.out.println(Seqs.deepToString(costly));

        while(true){
            current = open.poll();
            if(current == null) break;
//            if(costly[current.i][current.j] && block){
//                break;
//            }
            closed[current.i][current.j] = true;

            if(current.equals(grid[endI][endJ])){
                return;
            }

            Cell t;
            int multiplier;
            if(costly[current.i][current.j]){
                multiplier = 5;
            }else{
                multiplier = 1;
            }
            if(current.i - 1 >= 0){
                t = grid[current.i - 1][current.j];
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier);

                if(current.j - 1 >= 0){
                    t = grid[current.i - 1][current.j - 1];
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier);
                }

                if(current.j + 1 < grid[0].length){
                    t = grid[current.i - 1][current.j + 1];
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier);
                }
            }

            if(current.j - 1 >= 0){
                t = grid[current.i][current.j - 1];
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier);
            }

            if(current.j + 1 < grid[0].length){
                t = grid[current.i][current.j + 1];
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier);
            }

            if(current.i + 1 < grid.length){
                t = grid[current.i + 1][current.j];
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier);

                if(current.j - 1 >= 0){
                    t = grid[current.i + 1][current.j - 1];
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier);
                }

                if(current.j + 1 < grid[0].length){
                    t = grid[current.i + 1][current.j + 1];
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier);
                }
            }
        }
    }

    /*
    Params :
    tCase = test case No.
    x, y = Board's dimensions
    si, sj = start location's x and y coordinates
    ei, ej = end location's x and y coordinates
    int[][] blocked = Seq containing inaccessible cell coordinates
    */
    public static void test(int tCase, int x, int y, int si, int sj, int ei, int ej, int[][] blocked){
        System.out.println("\n\nmindustry.client.utils.Test Case #" + tCase);
        //Reset
        grid = new Cell[x][y];
        closed = new boolean[x][y];
        open = new PriorityQueue<>((Object o1, Object o2) -> {
            Cell c1 = (Cell)o1;
            Cell c2 = (Cell)o2;

            return Integer.compare(c1.finalCost, c2.finalCost);
        });
        //Set start position
        setStartCell(si, sj);  //Setting to 0,0 by default. Will be useful for the UI part

        //Set End Location
        setEndCell(ei, ej);

        for(int i = 0; i < x; ++i){
            for(int j = 0; j < y; ++j){
                grid[i][j] = new Cell(i, j);
                grid[i][j].heuristicCost = Math.abs(i - endI) + Math.abs(j - endJ);
//                  System.out.print(grid[i][j].heuristicCost+" ");
            }
//              System.out.println();
        }
        grid[si][sj].finalCost = 0;

           /*
             Set blocked cells. Simply set the cell values to null
             for blocked cells.
           */
        for(int[] ints : blocked){
            setBlocked(ints[0], ints[1]);
        }

        //Display initial map
        System.out.println("Grid: ");
        for(int i = 0; i < x; ++i){
            for(int j = 0; j < y; ++j){
                if(i == si && j == sj) System.out.print("SO  "); //Source
                else if(i == ei && j == ej) System.out.print("DE  ");  //Destination
                else if(grid[i][j] != null) System.out.printf("%-3d ", 0);
                else System.out.print("BL  ");
            }
            System.out.println();
        }
        System.out.println();

        AStarSearch();
        System.out.println("\nScores for cells: ");
        for(int i = 0; i < x; ++i){
            for(int j = 0; j < x; ++j){
                if(grid[i][j] != null) System.out.printf("%-3d ", grid[i][j].finalCost);
                else System.out.print("BL  ");
            }
            System.out.println();
        }
        System.out.println();

        if(closed[endI][endJ]){
            //Trace back the path
            System.out.println("Path: ");
            Cell current = grid[endI][endJ];
            System.out.print(current);
            while(current.parent != null){
                System.out.print(" -> " + current.parent);
                current = current.parent;
            }
            System.out.println();
        }else System.out.println("No possible path");
    }

    public static Seq<int[]> findPathWithObstacles(float playerX, float playerY, float targetX, float targetY, int width, int height, Team team, Seq<TurretPathfindingEntity> obstacles){
        int resolution = 2;  // The resolution of the map is divided by this value
        Seq<TurretPathfindingEntity> pathfindingEntities = new Seq<>();
        for(TurretPathfindingEntity zone : obstacles){
            if (zone == null) continue;
            pathfindingEntities.add(new TurretPathfindingEntity(zone.x / resolution, zone.y / resolution, (zone.range / tilesize) / resolution));
        }
        block = true;
        Seq<int[]> path = findPath(pathfindingEntities, playerX / resolution, playerY / resolution, targetX / resolution, targetY / resolution, width / resolution, height / resolution);
        Seq<int[]> output = new Seq<>();
        if(path == null){
            block = false;
            // Path blocked, retrying with cost
            path = findPath(pathfindingEntities, playerX / resolution, playerY / resolution, targetX / resolution, targetY / resolution, width / resolution, height / resolution);
        }
        if(path == null){
            return null;
        }
        for(int[] item : path){
            output.add(new int[]{item[0] * resolution, item[1] * resolution});
        }
        return output;
    }

    public static Seq<int[]> findPathTurrets(Seq<TurretBuild> turrets, float playerX, float playerY, float targetX, float targetY, int width, int height, Team team){
        int resolution = 2;  // The resolution of the map is divided by this value
        Seq<TurretPathfindingEntity> pathfindingEntities = new Seq<>();
        for(TurretBuild turretEntity : turrets){
            if(turretEntity.team == team){
                continue;
            }
            boolean flying = Vars.player.unit().isFlying();
            boolean targetsAir = ((Turret)turretEntity.block).targetAir;
            boolean targetsGround = ((Turret)turretEntity.block).targetGround;
            if(flying && !targetsAir){
                continue;
            }
            if(!flying && !targetsGround){
                continue;
            }
            pathfindingEntities.add(new TurretPathfindingEntity(turretEntity.tileX() / resolution, turretEntity.tileY() / resolution, ((Turret)turretEntity.block).range / (8 * resolution)));
        }
        block = true;
        Seq<int[]> path = findPath(pathfindingEntities, playerX / resolution, playerY / resolution, targetX / resolution, targetY / resolution, width / resolution, height / resolution);
        Seq<int[]> output = new Seq<>();
        if(path == null){
            block = false;
            // Path blocked, retrying with cost
            path = findPath(pathfindingEntities, playerX / resolution, playerY / resolution, targetX / resolution, targetY / resolution, width / resolution, height / resolution);
        }
        if(path == null){
            return null;
        }
        for(int[] item : path){
            output.add(new int[]{item[0] * resolution, item[1] * resolution});
        }
        return output;
    }

    public static Seq<int[]> findPath(Seq<TurretPathfindingEntity> turrets, float playerX, float playerY, float targetX, float targetY, int width, int height){
        playerX = Mathf.clamp(playerX, 0, width * 8);
        playerY = Mathf.clamp(playerY, 0, height * 8);
        targetX = Mathf.clamp(targetX, 0, width * 8);
        targetY = Mathf.clamp(targetY, 0, height * 8);
        if(turrets.size == 0){
            Seq<int[]> out = new Seq<>();
            out.add(new int[]{(int)targetX / 8, (int)targetY / 8});
            return out;
        }
//        long startTime = System.currentTimeMillis();
        ArrayList<int[]> blocked2 = new ArrayList<>();
        for(TurretPathfindingEntity turret : turrets){
//            if(turret.getTeam() == player.getTeam()){
//                continue;
//            }
            float range = turret.range;
            float x = turret.x;
            float y = turret.y;
            for(int colNum = 0; colNum <= width - 1; colNum += 1){
                if(colNum > x + range){
                    continue;
                }
                if(colNum < x - range){
                    continue;
                }

                for(int blockNum = 0; blockNum <= height - 1; blockNum += 1){
                    if(blockNum > y + range){
                        continue;
                    }
                    if(blockNum < y - range){
                        continue;
                    }

                    if(Mathf.sqrt(Mathf.pow(x - colNum, 2) + Mathf.pow(y - blockNum, 2)) < range){
                        blocked2.add(new int[]{colNum, blockNum});
                    }
                }
            }
        }
        int[][] blocked = new int[blocked2.size()][2];
        for(int b = 0; b <= blocked2.size() - 1; b += 1){
            blocked[b] = blocked2.get(b);
        }
        //Reset
        int px = (int)playerX / 8;
        int py = (int)playerY / 8;

        int ex = (int)targetX / 8;
        int ey = (int)targetY / 8;
        grid = null;
        grid = new Cell[width][height];
        closed = null;
        closed = new boolean[width][height];
        costly = null;
        costly = new boolean[width][height];
        open.clear();
        open = new PriorityQueue<>((Object o1, Object o2) -> {
            Cell c1 = (Cell)o1;
            Cell c2 = (Cell)o2;

            return Integer.compare(c1.finalCost, c2.finalCost);
        });
        //Set start position
        setStartCell(px, py);
        if(costly[px][py]){
            costly[px][py] = false;
        }

        //Set End Location
        setEndCell(ex, ey);

        for(int i = 0; i < width; ++i){
            for(int j = 0; j < height; ++j){
                grid[i][j] = new Cell(i, j);
                grid[i][j].heuristicCost = Math.abs(i - endI) + Math.abs(j - endJ);
//                  System.out.print(grid[i][j].heuristicCost+" ");
            }
//              System.out.println();
        }
        grid[px][py].finalCost = 0;

           /*
             Set blocked cells. Simply set the cell values to null
             for blocked cells.
           */
        for(int[] ints : blocked){
            if(block){
                setBlocked(ints[0], ints[1]);
            }else{
                costly[ints[0]][ints[1]] = true;
            }
        }
        grid[px][py] = new Cell(px, py);

        //Display initial map
//        System.out.println("Grid: ");
//        for(int i = 0; i < width; ++i){
//            for(int j = 0; j < height; ++j){
//                if(i == px && j == py) System.out.print("SO  "); //Source
//                else if(i == ex && j == ey) System.out.print("DE  ");  //Destination
//                else if(grid[i][j] != null) System.out.printf("%-3d ", 0);
//                else System.out.print("BL  ");
//            }
//            System.out.println();
//        }
//        System.out.println();
//        System.out.println("eifwief");
//        System.out.println(grid.length);
//        System.out.println(grid[0].length);
        AStarSearch();
//        System.out.println("\nScores for cells: ");
//        for(int i = 0; i < width; ++i){
//            for(int j = 0; j < height; ++j){
//                if(grid[i][j] != null) System.out.printf("%-3d ", grid[i][j].finalCost);
//                else System.out.print("BL  ");
//            }
//            System.out.println();
//        }
//        System.out.println();

        if(closed[endI][endJ]){
            Seq<int[]> points = new Seq<>();
            //Trace back the path
//            System.out.println("Path: ");
            Cell current = grid[endI][endJ];
            while(current.parent != null){
//                System.out.print(" -> " + current.parent);
                points.add(new int[]{current.parent.i, current.parent.j});
                current = current.parent;
            }
//            System.out.println("Time taken = " + (System.currentTimeMillis() - startTime) + " ms");
            return points;
//            System.out.println();
        }else{
//            System.out.println("Time taken = " + (System.currentTimeMillis() - startTime) + " ms, no path found");
            return null;
        }

    }

    public static void main(String[] args){
//        test(1, 5, 5, 0, 0, 3, 2, new int[][]{{0,4},{2,2},{3,1},{3,3}});
//        test(2, 5, 5, 0, 0, 4, 4, new int[][]{{0,4},{2,2},{3,1},{3,3}});
//        test(3, 7, 7, 2, 1, 5, 4, new int[][]{{4,1},{4,3},{5,3},{2,3}});
//
//        test(1, 5, 5, 0, 0, 4, 4, new int[][]{{3,4},{3,3},{4,3}});
        Random r = new Random();
        Seq<TurretPathfindingEntity> turrets = new Seq<>();
        for(int i = 0; i < 10; i += 1){
            int x = (int)(r.nextFloat() * 50);
            int y = (int)(r.nextFloat() * 50);
            turrets.add(new TurretPathfindingEntity(x, y, 5f));
        }
        findPath(turrets, 8f, 16f, 32f * 8, 48f * 8, 50, 50);
    }
}
