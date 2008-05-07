
package org.sakaiproject.evaluation.model;

// Generated Mar 20, 2007 10:08:13 AM by Hibernate Tools 3.2.0.beta6a

import java.util.Date;

/**
 * EvalScale generated by hbm2java
 */
public class EvalGroupNodes implements java.io.Serializable {

   // Fields

   private Long id;

   private Date lastModified;

   private String nodeId;

   private String[] evalGroups;

   // Constructors

   /** default constructor */
   public EvalGroupNodes() {
   }

   /** minimal constructor */
   public EvalGroupNodes(Date lastModified, String nodeId) {
      this.lastModified = lastModified;
      this.nodeId = nodeId;
   }

   /** full constructor */
   public EvalGroupNodes(Date lastModified, String nodeId, String[] evalGroups) {
      super();
      this.lastModified = lastModified;
      this.nodeId = nodeId;
      this.evalGroups = evalGroups;
   }

   // Property accessors

   public String[] getEvalGroups() {
      return evalGroups;
   }

   public void setEvalGroups(String[] evalGroups) {
      this.evalGroups = evalGroups;
   }

   public Long getId() {
      return id;
   }

   public void setId(Long id) {
      this.id = id;
   }

   public Date getLastModified() {
      return lastModified;
   }

   public void setLastModified(Date lastModified) {
      this.lastModified = lastModified;
   }

   public String getNodeId() {
      return nodeId;
   }

   public void setNodeId(String nodeId) {
      this.nodeId = nodeId;
   }

}
