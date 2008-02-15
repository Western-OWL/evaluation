/**
 * $Id: EvalDeliveryServiceImpl.java 1000 Jan 25, 2008 10:07:31 AM azeckoski $
 * $URL: https://source.sakaiproject.org/contrib $
 * EvalDeliveryServiceImpl.java - evaluation - Dec 25, 2006 10:07:31 AM - azeckoski
 **************************************************************************
 * Copyright (c) 2008 Centre for Applied Research in Educational Technologies, University of Cambridge
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 *
 * Aaron Zeckoski (azeckoski@gmail.com) (aaronz@vt.edu) (aaron@caret.cam.ac.uk)
 */

package org.sakaiproject.evaluation.logic;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.evaluation.dao.EvaluationDao;
import org.sakaiproject.evaluation.logic.EvalAuthoringService;
import org.sakaiproject.evaluation.logic.EvalEvaluationService;
import org.sakaiproject.evaluation.logic.EvalDeliveryService;
import org.sakaiproject.evaluation.logic.EvalSettings;
import org.sakaiproject.evaluation.logic.externals.EvalExternalLogic;
import org.sakaiproject.evaluation.model.EvalAnswer;
import org.sakaiproject.evaluation.model.EvalEvaluation;
import org.sakaiproject.evaluation.model.EvalItem;
import org.sakaiproject.evaluation.model.EvalResponse;
import org.sakaiproject.evaluation.model.EvalTemplateItem;
import org.sakaiproject.evaluation.model.constant.EvalConstants;
import org.sakaiproject.evaluation.utils.ArrayUtils;
import org.sakaiproject.evaluation.utils.EvalUtils;
import org.sakaiproject.evaluation.utils.TemplateItemUtils;
import org.sakaiproject.genericdao.api.finders.ByPropsFinder;

/**
 * Implementation for EvalDeliveryService
 * 
 * @author Aaron Zeckoski (aaronz@vt.edu)
 */
public class EvalDeliveryServiceImpl implements EvalDeliveryService {

   private static Log log = LogFactory.getLog(EvalDeliveryServiceImpl.class);

   // Event names cannot be over 32 chars long              // max-32:12345678901234567890123456789012
   protected final String EVENT_RESPONSE_CREATED =                   "eval.response.created";
   protected final String EVENT_RESPONSE_UPDATED =                   "eval.response.updated";

   private EvaluationDao dao;
   public void setDao(EvaluationDao dao) {
      this.dao = dao;
   }

   private EvalExternalLogic external;
   public void setExternalLogic(EvalExternalLogic external) {
      this.external = external;
   }

   private EvalEvaluationService evaluationService;
   public void setEvaluationService(EvalEvaluationService evaluationService) {
      this.evaluationService = evaluationService;
   }

   private EvalSettings evalSettings;
   public void setEvalSettings(EvalSettings evalSettings) {
      this.evalSettings = evalSettings;
   }

   private EvalAuthoringService authoringService;
   public void setAuthoringService(EvalAuthoringService authoringService) {
      this.authoringService = authoringService;
   }



   // INIT method
   public void init() {
      log.debug("Init");
   }


   public EvalResponse getResponseById(Long responseId) {
      log.debug("responseId: " + responseId);
      // get the response by passing in id
      EvalResponse response = (EvalResponse) dao.findById(EvalResponse.class, responseId);
      return response;
   }


   @SuppressWarnings("unchecked")
   public EvalResponse getEvaluationResponseForUserAndGroup(Long evaluationId, String userId, String evalGroupId) {
      EvalEvaluation evaluation = (EvalEvaluation) dao.findById(EvalEvaluation.class, evaluationId);
      if (evaluation == null) {
         throw new IllegalArgumentException("Invalid evaluation, cannot find evaluation: " + evaluationId);
      }

      EvalResponse response = null;
      List<EvalResponse> responses = dao.findByProperties(EvalResponse.class, 
            new String[] { "owner", "evaluation.id", "evalGroupId" }, 
            new Object[] { userId, evaluationId, evalGroupId }
         );
      if (responses.isEmpty()) {
         // create a new response and save it
         response = new EvalResponse(new Date(), userId, evalGroupId, new Date(), evaluation);
         saveResponse(response, userId);
      } else {
         if (responses.size() == 1) {
            response = responses.get(0);
         } else {
            throw new IllegalStateException("Invalid responses state, this user ("+userId+") has more than 1 response " +
            		"for evaluation ("+evaluationId+") and evalGroupId ("+evalGroupId+")");
         }
      }

      return response;
   }

   @SuppressWarnings("unchecked")
   public List<EvalResponse> getEvaluationResponses(String userId, Long[] evaluationIds, Boolean completed) {
      log.debug("userId: " + userId + ", evaluationIds: " + evaluationIds);

      if (evaluationIds.length <= 0) {
         throw new IllegalArgumentException("evaluationIds cannot be empty");
      }

      // check that the ids are actually valid
      int count = dao.countByProperties(EvalEvaluation.class, new String[] { "id" }, new Object[] { evaluationIds });
      if (count != evaluationIds.length) {
         throw new IllegalArgumentException("One or more invalid evaluation ids in evaluationIds: " + evaluationIds);
      }

      List<String> props = new ArrayList<String>();
      List<Object> values = new ArrayList<Object>();
      List<Number> comparisons = new ArrayList<Number>();

      // basic search params
      props.add("evaluation.id");
      values.add(evaluationIds);
      comparisons.add(ByPropsFinder.EQUALS);

      // if user is admin then return all matching responses for this evaluation
      if (! external.isUserAdmin(userId)) {
         // not admin, only return the responses for this user
         props.add("owner");
         values.add(userId);
         comparisons.add(ByPropsFinder.EQUALS);
      }

      handleCompleted(completed, props, values, comparisons);

      return dao.findByProperties(EvalResponse.class, 
            props.toArray(new String[props.size()]), 
            values.toArray(new Object[values.size()]), 
            ArrayUtils.listToIntArray(comparisons), 
            new String[] { "id" });
   }

   public int countResponses(Long evaluationId, String evalGroupId, Boolean completed) {
      log.debug("evaluationId: " + evaluationId + ", evalGroupId: " + evalGroupId);

      if (dao.countByProperties(EvalEvaluation.class, new String[] { "id" }, new Object[] { evaluationId }) <= 0) {
         throw new IllegalArgumentException("Could not find evaluation with id: " + evaluationId);
      }

      List<String> props = new ArrayList<String>();
      List<Object> values = new ArrayList<Object>();
      List<Number> comparisons = new ArrayList<Number>();

      // basic search params
      props.add("evaluation.id");
      values.add(evaluationId);
      comparisons.add(ByPropsFinder.EQUALS);

      /* returns count of all responses in all eval groups if evalGroupId is null
       * and returns count of responses in this evalGroupId only if set
       */
      if (evalGroupId != null) {
         props.add("evalGroupId");
         values.add(evalGroupId);
         comparisons.add(ByPropsFinder.EQUALS);
      }

      handleCompleted(completed, props, values, comparisons);

      return dao.countByProperties(EvalResponse.class, 
            props.toArray(new String[props.size()]), 
            values.toArray(new Object[values.size()]), 
            ArrayUtils.listToIntArray(comparisons)
            );

   }

   /**
    * Reduce code duplication be breaking out this common code
    * @param completed
    * @param props
    * @param values
    * @param comparisons
    */
   private void handleCompleted(Boolean completed, List<String> props, List<Object> values, List<Number> comparisons) {
      if (completed != null) {
         // if endTime is null then the response is incomplete, if not null then it is complete
         props.add("endTime");
         values.add(""); // just need a placeholder
         if (completed) {
            comparisons.add(ByPropsFinder.NOT_NULL);
         } else {
            comparisons.add(ByPropsFinder.NULL);            
         }
      }
   }


   public List<EvalAnswer> getEvalAnswers(Long itemId, Long evaluationId, String[] evalGroupIds) {
      log.debug("itemId: " + itemId + ", evaluationId: " + evaluationId);

      if (dao.countByProperties(EvalItem.class, new String[] { "id" }, new Object[] { itemId }) <= 0) {
         throw new IllegalArgumentException("Could not find item with id: " + itemId);
      }

      if (dao.countByProperties(EvalEvaluation.class, new String[] { "id" }, new Object[] { evaluationId }) <= 0) {
         throw new IllegalArgumentException("Could not find evaluation with id: " + evaluationId);
      }

      List<EvalAnswer> answers = dao.getAnswers(itemId, evaluationId, evalGroupIds);
      for (EvalAnswer answer : answers) {
         // decode the stored answers into the int array
         answer.multipleAnswers = EvalUtils.decodeMultipleAnswers(answer.getMultiAnswerCode());
      }
      return answers;
   }

   public List<Long> getEvalResponseIds(Long evaluationId, String[] evalGroupIds, Boolean completed) {
      log.debug("evaluationId: " + evaluationId);

      if (dao.countByProperties(EvalEvaluation.class, new String[] { "id" }, new Object[] { evaluationId }) <= 0) {
         throw new IllegalArgumentException("Could not find evaluation with id: " + evaluationId);
      }

      return dao.getResponseIds(evaluationId, evalGroupIds, null, completed);
   }

   @SuppressWarnings("unchecked")
   public void saveResponse(EvalResponse response, String userId) {
      log.debug("userId: " + userId + ", response: " + response.getId() + ", evalGroupId: " + response.getEvalGroupId());

      // set the date modified
      response.setLastModified(new Date());

      boolean newResponse = true;
      if (response.getId() != null) {
         newResponse = false;
         // TODO - existing response, don't allow change to any setting
         // except starttime, endtime, and answers
      }

      // fill in any default values and nulls here

      // check perms and evaluation state
      if (checkUserModifyResponse(userId, response)) {
         // make sure the user can take this evalaution
         Long evaluationId = response.getEvaluation().getId();
         String evalGroupId = response.getEvalGroupId();
         if (! evaluationService.canTakeEvaluation(userId, evaluationId, evalGroupId)) {
            throw new IllegalStateException("User (" + userId + ") cannot take this evaluation (" + evaluationId
                  + ") in this evalGroupId (" + evalGroupId + ") right now");
         }

         // check to make sure answers are valid for this evaluation
         if (response.getAnswers() != null && !response.getAnswers().isEmpty()) {
            checkAnswersValidForEval(response);
         } else {
            if (response.getEndTime() != null) {
               // the response is complete (submission of an evaluation) and not just creating the empty response
               // check if answers are required to be filled in
               Boolean unansweredAllowed = (Boolean)evalSettings.get(EvalSettings.STUDENT_ALLOWED_LEAVE_UNANSWERED);
               if (unansweredAllowed == null) {
                  unansweredAllowed = response.getEvaluation().getBlankResponsesAllowed();
               }
               if (unansweredAllowed.booleanValue() == false) {
                  // all items must be completed so die if they are not
                  throw new IllegalStateException("User submitted a blank response and there are required answers");
               }
            }
            response.setAnswers(new HashSet());
         }

         // save everything in one transaction

         // response has to be saved first
         Set<EvalResponse> responseSet = new HashSet<EvalResponse>();
         responseSet.add(response);

         Set<EvalAnswer> answersSet = response.getAnswers();

         dao.saveMixedSet(new Set[] {responseSet, answersSet});

         String completeMessage = ", response is incomplete";
         if (response.getEndTime() != null) {
            // the response is complete (submission of an evaluation) and not just creating the empty response
            // so lock evaluation
            log.info("Locking evaluation (" + response.getEvaluation().getId() + ") and associated entities");
            EvalEvaluation evaluation = (EvalEvaluation) dao.findById(EvalEvaluation.class, response.getEvaluation().getId());
            dao.lockEvaluation(evaluation);
            completeMessage = ", response is complete";
         }

         if (newResponse) {
            external.registerEntityEvent(EVENT_RESPONSE_CREATED, response);
         } else {
            external.registerEntityEvent(EVENT_RESPONSE_UPDATED, response);            
         }
         int answerCount = response.getAnswers() == null ? 0 : response.getAnswers().size();
         log.info("User (" + userId + ") saved response (" + response.getId() + ") to" +
         		"evaluation ("+evaluationId+") for groupId (" + response.getEvalGroupId() + ") " +
         		" with " + answerCount + " answers" + completeMessage);
         return;
      }

      // should not get here so die if we do
      throw new RuntimeException("User (" + userId + ") could NOT save response (" + response.getId()
            + "), evalGroupId: " + response.getEvalGroupId());
   }

   // PERMISSIONS

   public boolean canModifyResponse(String userId, Long responseId) {
      log.debug("userId: " + userId + ", responseId: " + responseId);
      // get the response by id
      EvalResponse response = (EvalResponse) dao.findById(EvalResponse.class, responseId);
      if (response == null) {
         throw new IllegalArgumentException("Cannot find response with id: " + responseId);
      }

      // valid state, check perms and locked
      try {
         return checkUserModifyResponse(userId, response);
      } catch (RuntimeException e) {
         log.info(e.getMessage());
      }
      return false;
   }

   // INTERNAL METHODS

   /**
    * Check if user has permission to modify this response
    * 
    * @param userId
    * @param response
    * @return true if they do, exception otherwise
    */
   protected boolean checkUserModifyResponse(String userId, EvalResponse response) {
      log.debug("evalGroupId: " + response.getEvalGroupId() + ", userId: " + userId);

      String state = EvalUtils.getEvaluationState(response.getEvaluation());
      if (EvalConstants.EVALUATION_STATE_ACTIVE.equals(state) || EvalConstants.EVALUATION_STATE_ACTIVE.equals(state)) {
         // admin CAN save responses -AZ
//       // check admin (admins can never save responses)
//       if (external.isUserAdmin(userId)) {
//       throw new IllegalArgumentException("Admin user (" + userId + ") cannot create response ("
//       + response.getId() + "), admins can never save responses");
//       }

         // check ownership
         if (response.getOwner().equals(userId)) {
            return true;
         } else {
            throw new SecurityException("User (" + userId + ") cannot modify response (" + response.getId()
                  + ") without permissions");
         }
      } else {
         throw new IllegalStateException("Evaluation state (" + state + ") not valid for modifying responses");
      }
   }

   /**
    * Checks the answers in the response for validity<br/>
    * 
    * @param response
    * @return true if all answers valid, exception otherwise
    */
   protected boolean checkAnswersValidForEval(EvalResponse response) {

      // get a list of the valid templateItems for this evaluation
      EvalEvaluation eval = response.getEvaluation();
      Long evalId = eval.getId();
      // TODO - make this more efficient by limiting the nodes, instructors, and groups
      List<EvalTemplateItem> allTemplateItems = authoringService.getTemplateItemsForEvaluation(evalId, new String[] {}, new String[] {}, new String[] {});

      // OLD - this should use a method elsewhere -AZ
//      EvalEvaluation eval = response.getEvaluation();
//      Long templateId = eval.getTemplate().getId();
//      List<EvalTemplateItem> allTemplateItems = 
//         dao.findByProperties(EvalTemplateItem.class, 
//               new String[] { "template.id" },
//               new Object[] { templateId });

      List<EvalTemplateItem> templateItems = TemplateItemUtils.getAnswerableTemplateItems(allTemplateItems);

      // put all templateItemIds into a set for easy comparison
      Set<Long> templateItemIds = new HashSet<Long>();
      for (int i = 0; i < templateItems.size(); i++) {
         templateItemIds.add(((EvalTemplateItem) templateItems.get(i)).getId());
      }

      // check the answers
      Set<Long> answeredTemplateItemIds = new HashSet<Long>();
      for (Iterator<EvalAnswer> iter = response.getAnswers().iterator(); iter.hasNext();) {
         EvalAnswer answer = (EvalAnswer) iter.next();
         if (answer.getNumeric() == null && answer.getText() == null && 
               (answer.getMultiAnswerCode() == null || answer.getMultiAnswerCode().length() == 0) ) {
            throw new IllegalArgumentException("Cannot save blank answers: answer for templateItem: "
                  + answer.getTemplateItem().getId());
         }

         // make sure the item and template item are available for this answer
         if (answer.getTemplateItem() == null || answer.getTemplateItem().getItem() == null) {
            throw new IllegalArgumentException("NULL templateItem or templateItem.item for answer: " +
            "Answers must have the templateItem set, and that must have the item set");
         }

         // verify the base state of new answers
         if (answer.getId() == null) {
            // force the item to be set correctly
            answer.setItem(answer.getTemplateItem().getItem());

            // check that the associated id is filled in for associated items
            if (EvalConstants.ITEM_CATEGORY_COURSE.equals(answer.getTemplateItem().getCategory())) {
               if (answer.getAssociatedId() != null) {
                  log.warn("Course answers should have the associated id field blank, for templateItem: "
                        + answer.getTemplateItem().getId());
               }
               answer.setAssociatedId(null);
               answer.setAssociatedType(null);
            } else if (EvalConstants.ITEM_CATEGORY_INSTRUCTOR.equals(answer.getTemplateItem().getCategory())) {
               if (answer.getAssociatedId() == null) {
                  throw new IllegalArgumentException(
                        "Instructor answers must have the associated field filled in with the instructor userId, for templateItem: "
                        + answer.getTemplateItem().getId());
               }
               answer.setAssociatedType(EvalConstants.ITEM_CATEGORY_INSTRUCTOR);
            } else if (EvalConstants.ITEM_CATEGORY_ENVIRONMENT.equals(answer.getTemplateItem().getCategory())) {
               if (answer.getAssociatedId() == null) {
                  throw new IllegalArgumentException(
                        "Environment answers must have the associated field filled in with the unique environment id, for templateItem: "
                        + answer.getTemplateItem().getId());
               }
               answer.setAssociatedType(EvalConstants.ITEM_CATEGORY_ENVIRONMENT);
            } else {
               throw new IllegalArgumentException("Do not know how to handle a templateItem category of ("
                     + answer.getTemplateItem().getCategory() + ") for templateItem: "
                     + answer.getTemplateItem().getId());
            }

            // make sure answer is associated with a valid templateItem for this evaluation
            if (! templateItemIds.contains(answer.getTemplateItem().getId())) {
               throw new IllegalArgumentException("This answer templateItem (" + answer.getTemplateItem().getId()
                     + ") is not part of this evaluation (" + response.getEvaluation().getTitle() + ")");
            }
         }

         // TODO - check if numerical answers are valid?

         // add to the answered items set
         answeredTemplateItemIds.add(answer.getTemplateItem().getId());
      }

      // check if required answers are filled in
      Boolean unansweredAllowed = (Boolean)evalSettings.get(EvalSettings.STUDENT_ALLOWED_LEAVE_UNANSWERED);
      if (unansweredAllowed == null) {
         unansweredAllowed = eval.getBlankResponsesAllowed();
      }
      if (unansweredAllowed.booleanValue() == false) {
         // all items must be completed so die if they are not
         List<EvalTemplateItem> requiredTemplateItems = TemplateItemUtils.getRequiredTemplateItems(templateItems);
         int missingRequired = 0;
         for (EvalTemplateItem templateItem : requiredTemplateItems) {
            if (! answeredTemplateItemIds.contains(templateItem.getId())) {
               missingRequired++;
            }
         }
         if (missingRequired > 0) {
            throw new IllegalStateException("Missing " + missingRequired + " required items for this evaluation response: " + response.getId());
         }
      }

      return true;
   }

}
