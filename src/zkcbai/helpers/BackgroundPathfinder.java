/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import zkcbai.helpers.Pathfinder.PathfinderRequest;

/**
 *
 * @author User
 */
public class BackgroundPathfinder implements Runnable {

    Pathfinder pathfinder;
    
    public BackgroundPathfinder(Pathfinder pathfinder){
        this.pathfinder = pathfinder;
    }
    
    @Override
    public void run() {
        while(true){
            if (pathfinder.getPathfinderRequests().isEmpty()){
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                }
                continue;
            }
            PathfinderRequest request = pathfinder.getPathfinderRequests().poll();
            request.listener.foundPath(pathfinder.findPath(request.start, request.target, request.movementType, request.costs, request.markReachable));
            
        }
    }
    
}
