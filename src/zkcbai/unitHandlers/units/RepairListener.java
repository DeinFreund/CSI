/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

/**
 *
 * @author User
 */
public interface RepairListener {
    
    public void retreating(AIUnit u);
    
    public void finishedRepairs(AIUnit u);
}
