/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import zkcbai.helpers.ZoneManager.Area;

/**
 *
 * @author User
 */
public interface CostSupplier {
    
    float getCost(Area pos);
}
