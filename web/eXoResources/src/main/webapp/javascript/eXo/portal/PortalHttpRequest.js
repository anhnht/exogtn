/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

/***********************************************************************************************
 * Portal  Ajax  Response Data Structure
 * {PortalResponse}
 *      |
 *      |--->{PortletResponse}
 *      |
 *      |--->{PortletResponse}
 *      |          |-->{portletId}
 *      |          |
 *      |          |-->{PortletResponseData}
 *      |                 |
 *      |                 |--->{BlockToUpdate}
 *      |                 |         |-->{BlockToUpdateId}
 *      |                 |         |-->{BlockToUpdateData}
 *      |                 |
 *      |                 |--->{BlockToUpdate}
 *      |
 *      |--->{PortalResponseData}
 *      |      |
 *      |      |--->{BlockToUpdate}
 *      |      |         |-->{BlockToUpdateId}
 *      |      |         |-->{BlockToUpdateData}
 *      |      |
 *      |      |--->{BlockToUpdate}
 *      |--->{PortalResponseScript}
 *
 **************************************************************************************************/

/*
* This object is wrapper on the value of each HTML block
* returned by an eXo Portal AJAX call. 
*
* This includes:
*    - the portle ID
*    - the portlet title
*    - the portlet mode
*    - the portlet state
*    - the portlet content
*    - the updated scripts to dynamically load in the browser
*
* Then each block to update within the portlet are place in a object
* which is itself placed inside an array to provide an OO view of the
* AJAX response
*/

function PortletResponse(responseDiv) {
  var  DOMUtil = eXo.core.DOMUtil ;
  var div = eXo.core.DOMUtil.getChildrenByTagName(responseDiv, "div") ;
  this.portletId =  div[0].innerHTML ;
  this.portletData =  div[1].innerHTML ;
  this.blocksToUpdate = null ;
  var blocks = DOMUtil.findChildrenByClass(div[1], "div", "BlockToUpdate") ;
  if(blocks.length > 0 ) {
    this.blocksToUpdate = new Array() ;
    for(var i = 0 ; i < blocks.length; i++) {
      var obj = new Object() ; 
      var div = eXo.core.DOMUtil.getChildrenByTagName(blocks[i], "div") ;
      obj.blockId = div[0].innerHTML ;
      obj.data = div[1] ;
      this.blocksToUpdate[i] = obj ;
      this.blocksToUpdate[i].scripts = eXo.core.DOMUtil.findDescendantsByTagName(div[1], "script") ;
    }
  } else {
    /*
    * If there is no block to update it means we are in a JSR 286 / 168 portlet
    * In that case we need to find all the script tags and dynamically execute them.
    *
    * Indeed, when being in an AJAX call that return some <script> tag, local functions are
    * lost when calling the eval() methods. When the code is written by eXo we use 
    * global scoped funstions like "instance.myFunction = function(arguments)".
    *
    * But when the code is provided by a third party portlet, it is not possible to
    * force that good practise. Hence we have to dynamically reference the embedded
    * script in the head tag
    */
    
    this.scripts = eXo.core.DOMUtil.findDescendantsByTagName(div[1], "script") ;
  }
};

/*****************************************************************************************/
/*
* This object is an OO wrapper on top of the returning HTML included in the PortalResponse
* tag. 
*
* It allows to split in two different arrays the portletResponse blocks and the one and the
* PortalResponseData one
*
* It also extract from the HTML the javascripts script to then be dynamically evaluated
*/
function PortalResponse(responseDiv) {
  var  DOMUtil = eXo.core.DOMUtil ;
  this.portletResponses = new Array() ;
  var div = DOMUtil.getChildrenByTagName(responseDiv, "div") ;
  for(var i = 0 ; i < div.length; i++) {
    if(div[i].className == "PortletResponse") {
      this.portletResponses[this.portletResponses.length] =  new PortletResponse(div[i]) ;
    } else if(div[i].className == "PortalResponseData") {
      this.data = div[i] ;
      var blocks = DOMUtil.findChildrenByClass(div[i], "div", "BlockToUpdate") ;
      this.blocksToUpdate = new Array() ;
      for(var j = 0 ; j < blocks.length; j++) {
        var obj = new Object() ; 
        var dataBlocks = DOMUtil.getChildrenByTagName(blocks[j], "div") ;
        obj.blockId = dataBlocks[0].innerHTML ;
        obj.data = dataBlocks[1] ;
        this.blocksToUpdate[j] = obj ;
        
        /*
        * handle embedded javascripts to dynamically add them to the page head
        *
        * This is needed when we refresh an entire portal page that contains some 
        * standard JSR 168 / 286 portlets with embeded <script> tag
        */
        this.blocksToUpdate[j].scripts = eXo.core.DOMUtil.findDescendantsByTagName(dataBlocks[1], "script") ;
        
      }
		} else if(div[i].className == "MarkupHeadElements") {
			this.markupHeadElements = new MarkupHeadElements(div[i]);
    } else if(div[i].className == "PortalResponseScript") {
      this.script = div[i].innerHTML ;
			div[i].style.display = "none" ;
    }
  }
};

function MarkupHeadElements(fragment) {
	var  DOMUtil = eXo.core.DOMUtil ;
	this.titles = DOMUtil.findDescendantsByTagName(fragment, "title");
	this.bases = DOMUtil.findDescendantsByTagName(fragment, "base") ;
	this.links = DOMUtil.findDescendantsByTagName(fragment, "link") ;
	this.metas = DOMUtil.findDescendantsByTagName(fragment, "meta") ;
	this.scripts = DOMUtil.findDescendantsByTagName(fragment, "script") ;           
	this.styles = DOMUtil.findDescendantsByTagName(fragment, "style") ;
}

/*
* This function is used to dynamically append a script to the head tag
* of the page
*/
function appendScriptToHead(scriptId, scriptElement) {
  var head = document.getElementsByTagName("head")[0]; 
  var descendant = eXo.core.DOMUtil.findDescendantById(head, scriptId);
  var script;
  if(descendant) {
    head.removeChild(descendant) ;
  }

  script = document.createElement('script');
  script.id = scriptId;
  script.type = 'text/javascript';
  
  //check if contains source attribute
  if(scriptElement.src) {
    script.src = scriptElement.src;
  } else {
  	script.text = scriptElement.innerHTML;
  }
  head.appendChild(script);
};



/*****************************************************************************************/
/*
* This is the main object that acts both as a field wrapper and a some status method wrapper
*
* It is also the object that has the reference to the XHR request thanks to a reference to 
* the eXo.core.Browser object
*/
function AjaxRequest(method, url, queryString) {	
	var instance = new Object() ;
	
	instance.timeout = 80000 ;
	instance.aborted = false ;
	
	if(method != null) instance.method = method; else	instance.method = "GET" ;
	if(url != null) instance.url = url; else instance.url = window.location.href ;
	if(queryString != null) instance.queryString = queryString; else instance.queryString = null ;

	instance.request = null ;
	
	instance.responseReceived = false ;

	instance.status = null ;
	instance.statusText = null ;
	
	instance.responseText = null ;
	instance.responseXML = null ;
	
	instance.onTimeout = null ;
	instance.onLoading = null ;
	instance.onLoaded = null ;
	instance.onInteractive = null ;
	instance.onComplete = null ;
	instance.onSuccess = null ;
	instance.callBack = null ;

	instance.onError = null;
	
	instance.isAsynchronize = function() {		
		var isASync = false;
		var name = "ajax_async";
		name = name.replace(/[\[]/,"\\\[").replace(/[\]]/,"\\\]"); 
		var regexS = "[\\?&]"+name+"=([^&#]*)"; 
		var regex = new RegExp( regexS );		
		var results = regex.exec( instance.url );		
		if( results != null ) {			
			isASync = (results[1] == "true") ? true : false;			
		}
		return isASync;
	};	
	
	instance.onLoadingInternalHandled = false ;
	instance.onLoadedInternalHandled = false ;
	instance.onInteractiveInternalHandled = false ;
	instance.onCompleteInternalHandled = false ;
	
	instance.request = eXo.core.Browser.createHttpRequest() ;
	
	/*
	* This method is called several times during the AJAX request call, in
	* fact each time the request state changes. In each case the call is 
	* delegated to one of the method of the AjaxRequest instance
	*/
	instance.request.onreadystatechange = function() {
		if (instance == null || instance.request == null) { return; }
		if (instance.request.readyState == 1) { instance.onLoadingInternal(instance) ; }
		if (instance.request.readyState == 2) { instance.onLoadedInternal(instance) ; }
		if (instance.request.readyState == 3) { instance.onInteractiveInternal(instance) ; }
		if (instance.request.readyState == 4) { instance.onCompleteInternal(instance) ; }
    } ;
	
    /*
    * This method is executed only if the boolean "onLoadingInternalHandled" is set to false
    * The method delegate the call to the ajaxLoading() method of the HttpResponseHandler
    */
	instance.onLoadingInternal = function() {
		if (instance.onLoadingInternalHandled) return ; 

		if (typeof(instance.onLoading) == "function") instance.onLoading(instance) ;
		instance.onLoadingInternalHandled = true ;
	} ;
	
    /*
    * This method is executed only if the boolean "onLoadedInternalHandled" is set to false
    * The method delegate the call to the instance.onLoaded() which is null for now
    */	
	instance.onLoadedInternal = function() {
		if (instance.onLoadedInternalHandled) return ;
		if (typeof(instance.onLoaded) == "function") instance.onLoaded(instance) ;
		instance.onLoadedInternalHandled = true ;
	} ;
	
    /*
    * This method is executed only if the boolean "onInteractiveInternalHandled" is set to false
    * The method delegate the call to the instance.onInteractive() which is null for now
    */		
	instance.onInteractiveInternal = function() {
		if (instance.onInteractiveInternalHandled) return ;
		if (typeof(instance.onInteractive) == "function") instance.onInteractive(instance) ;
		instance.onInteractiveInternalHandled = true ;
	} ;

	/**
	 * evaluate the response and return an object
	 */
  	instance.evalResponse = function() {
		try {
		  	return eval((instance.responseText || ''));
		} catch (e) {
		  	throw (new Error('Cannot eval the response')) ;
		}
	};
	
	
    /*
    * This method is executed only if the boolean "onCompleteInternalHandled" is set to false
    * The method delegate the call to the ajaxResponse() method of the HttpResponseHandler after
    * calling the onSuccess() method of the current object
    *
    * During the processof this method, all the instance fields are filled with the content coming 
    * back from the AJAX call. Once the ajaxResponse() is called then the callback object is called
    * if not null
    */	
	instance.onCompleteInternal = function() {
		if (instance.onCompleteInternalHandled || instance.aborted) return ; 
		
		try{
			instance.responseReceived = true ;
			instance.status = instance.request.status ;
			instance.statusText = instance.request.statusText ;
			instance.responseText = instance.request.responseText ;
			instance.responseXML = instance.request.responseXML ;
		}catch(err){
			instance.status = 0;
		}
		
		if(typeof(instance.onComplete) == "function") instance.onComplete(instance) ;
		
		if (instance.status == 200 && typeof(instance.onSuccess) == "function") {
			instance.onSuccess(instance) ;
			instance.onCompleteInternalHandled = true ;
			if (typeof(instance.callBack) == "function") {
			  instance.callBack(instance) ;
			} else if (instance.callBack) { // Modified by Uoc Nguyen: allow user use custom javascript code for callback
			  try {
			    eval(instance.callBack) ;
			  }
			  catch (e) {
          throw (new Error('Can not execute callback...')) ;
        }
			}
		} else if (typeof(instance.onError) == "function") {
			instance.onError(instance) ;
			instance.onCompleteInternalHandled = false ;
		}
		
		// Remove IE doesn't leak memory
		delete instance.request['onreadystatechange'] ;
		instance.request = null ;

	} ;
		
    /*
    * This method is executed only if the boolean "onLoadingInternalHandled" is set to false
    * The method delegate the call to the ajaxTimeout() method of the HttpResponseHandler
    */
	instance.onTimeoutInternal = function() {
		if (instance == null || instance.request == null || instance.onCompleteInternalHandled) return ;
		instance.aborted = true ;
		instance.request.abort() ;
		
		if (typeof(instance.onTimeout) == "function") instance.onTimeout(instance) ;
		
		delete instance.request['onreadystatechange'] ;
		instance.request = null ;
	} ;
	
	/*
	* This method is directly called from the doRequest() method. It opens a connection to the server,
	* set up the handlers and sends the query to it. Status methods are then called on the request object
	* during the entire lifecycle of the call
	*
	* It also sets up the time out and its call back to the method of the current instance onTimeoutInternal()
	*/
	instance.process = function() {
		if (instance.request == null) return;
		instance.request.open(instance.method, instance.url, true);		
		//instance.request.open(instance.method, instance.url, instance.isAsynchronize());		
		
		if (instance.method == "POST") {
			instance.request.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8") ;
		} else {
			instance.request.setRequestHeader("Content-Type", "text/plain;charset=UTF-8") ;
		}
		
		if (instance.timeout > 0) setTimeout(instance.onTimeoutInternal, instance.timeout) ;
		
		instance.request.send(instance.queryString);
	} ;
	
	return instance ;
} ;

/*****************************************************************************************/
/*
* This object is also a wrapper object on top of several methods: 
*   - executeScript
*   - updateBlocks
*   - ajaxTimeout
*   - ajaxResponse
*   - ajaxLoading
* 
* Those methods are executed during the process of the AJAX call
*/
function HttpResponseHandler(){
	var instance = new Object() ;
	/*
	 * instance.to stores a timeout object used to postpone the display of the loading popup
	 * the timeout is defined later in the instance.ajaxLoading function
	 */
	instance.to = null;
	/*
	* This internal method is used to dynamically load JS scripts in the 
	* browser by using the eval() method;
	*/
	instance.executeScript = function(script) {
	  if(script == null || script == "") return ;
	  try {
		var HTMLUtil = eXo.core.HTMLUtil;
		script = HTMLUtil.entitiesDecode(script);
	    eval(script) ;       
	    return;
	  } catch(err) {                  
	  }
	  var elements = script.split(';') ;
	  if(elements != null && elements.length > 0) {
		  for(var i = 0; i < elements.length; i++) {
		    try {
		      eval(elements[i]) ;
		    } catch(err) {
		      alert(err +" : "+elements[i] + "  -- " + i) ;      
		    }
		  }
	  } 
	} ;

	        

	instance.updateHtmlHead = function(response) {
		if (!response) return;      
		cleanHtmlHead(response);
		var DOMUtil = eXo.core.DOMUtil;
		var head = document.getElementsByTagName("head")[0]; 								
		var markupHeadElements = response.markupHeadElements;
		if (!markupHeadElements) return;
		
		if (markupHeadElements.titles && markupHeadElements.titles.length != 0) {
			var oldTitle = DOMUtil.getChildrenByTagName(head, "title")[0];
			var newTitle = markupHeadElements.titles[markupHeadElements.titles.length - 1];
			if (oldTitle) {
				head.replaceChild(newTitle, oldTitle);
			} else {
				head.appendChild(newTitle);
			}
		}                       

		appendElementsToHead(markupHeadElements.metas);
		appendElementsToHead(markupHeadElements.bases);
		appendElementsToHead(markupHeadElements.links);                         
		appendElementsToHead(markupHeadElements.styles);
		appendElementsToHead(markupHeadElements.scripts);
	};

	function cleanHtmlHead(response) {
		var DOMUtil = eXo.core.DOMUtil;
		var head = document.getElementsByTagName("head")[0];            
		var portletResponses = response.portletResponses;
		if (portletResponses) {
			for (var i = 0; i < portletResponses.length; i++) {
				removeExtraHead(portletResponses[i].portletId);
			}
		}

		if (response.data) {
			var portletFragments = DOMUtil.findDescendantsByClass(response.data, "div", "PORTLET-FRAGMENT");
			for (var i = 0; i < portletFragments.length; i++) {
				removeExtraHead(portletFragments[i].parentNode.id);
			}
		}
		
		var uiWorkingWorkspace = document.getElementById("UIWorkingWorkspace") ;
		var portletFragsInWS = DOMUtil.findDescendantsByClass(uiWorkingWorkspace, "div", "PORTLET-FRAGMENT");           
		var exHeads = DOMUtil.getElementsBy(function(elem) {
			return elem.tagName != "TITLE" && elem.className.indexOf("ExHead-") == 0;
		}, "*", head);

		for (var i = 0; i < exHeads.length; i++) {
			var portletId = exHeads[i].className.substring(7);
			var del = true;
			for (var j = 0; j < portletFragsInWS.length; j++) {
				if (portletId == portletFragsInWS[j].parentNode.id) {
					del = false;
					break;
				}
			}

			if (del) {
				head.removeChild(exHeads[i]);
			}
		}
	}

	function removeExtraHead(portletId) {
		var DOMUtil = eXo.core.DOMUtil;
		var head = document.getElementsByTagName("head")[0];
		var elemsToRemove = DOMUtil.getElementsBy(function(elem) {
			return elem.tagName != "TITLE" && elem.className == "ExHead-" + portletId;
		}, "*", head);

		for (var i = 0; i < elemsToRemove.length; i++) {
			head.removeChild(elemsToRemove[i]);
		}
	}

	function appendElementsToHead(elements) {
		if (!elements) return;
		var head = document.getElementsByTagName("head")[0]; 
		
		for (var i = 0; i < elements.length; i++) {
			head.appendChild(elements[i]);
		}
	}

	/*
	* This methods will replace some block content by new one. 
	* This is the important concept in any AJAX call where JS is used to dynamically
	* refresh a part of the page.
	* 
	* The first argument is an array of blocks to update while the second argument is 
	* the id of the html component that is the parent of the block to update
	* 
	* Each block in the array contains the exact id to update, hence a loop is executed 
	* for each block and the HTML is then dynamically replaced by the new one
	*/
	instance.updateBlocks = function(blocksToUpdate, parentId) {	  
	  if(blocksToUpdate == null) return ;
	  var parentBlock = null ;
	  if(parentId != null && parentId != "") parentBlock =  document.getElementById(parentId) ;
	  for(var i = 0; i < blocksToUpdate.length; i++) {
	    var blockToUpdate =  blocksToUpdate[i] ;
	 //   alert("block update" + blockToUpdate.blockId) ;	
	    var target = null ;   	
	    if(parentBlock != null) {
	    	target = eXo.core.DOMUtil.findDescendantById(parentBlock, blockToUpdate.blockId) ;
	    } else {
	    	target = document.getElementById(blockToUpdate.blockId) ;
	    }
	    if(target == null) alert(eXo.i18n.I18NMessage.getMessage("TargetBlockNotFound", new Array (blockToUpdate.blockId))) ;
	    var newData =  eXo.core.DOMUtil.findDescendantById(blockToUpdate.data, blockToUpdate.blockId) ;
	   	//var newData =  blockToUpdate.data.getElementById(blockToUpdate.blockId) ;
	    if(newData == null) alert(eXo.i18n.I18NMessage.getMessage("BlockUpdateNotFound", new Array (blockToUpdate.blockId))) ;
//	    target.parentNode.replaceChild(newData, target);
	    target.innerHTML = newData.innerHTML ;
	    //update embedded scripts
	    if(blockToUpdate.scripts) {
	      if(blockToUpdate.scripts.length > 0) {
          for(var k = 0 ; k < blockToUpdate.scripts.length; k++) {
            var encodedName = 'script_' + k + '_' +  blockToUpdate.blockId;
            appendScriptToHead(encodedName, blockToUpdate.scripts[k]);
          }
        }
	    }
	  }
	} ;
	
	/*
	* This method is called when the AJAX call was too long to be executed
	*/
	instance.ajaxTimeout = function(request){
//	  eXo.core.UIMaskLayer.removeMask(eXo.portal.AjaxRequest.maskLayer) ;
	  eXo.core.UIMaskLayer.removeMasks(eXo.portal.AjaxRequest.maskLayer) ;
	  eXo.portal.AjaxRequest.maskLayer = null ;
	  eXo.portal.CurrentRequest = null ;
	  window.location.reload() ;
	};
	
	/*
	* This method is called when the AJAX call is completed and that the request.responseText
	* has been filled with the returning HTML. Hence the goal of this method is to update the
	* diffent blocks dynamically.
	*
	* 1) Create a temporary div element and set the response HTML text to its innerHTML variable of the
	     temp object
	* 2) Use the DOMUtil.findFirstDescendantByClass() method to get the div with the Id "PortalResponse" 
	*    out of the returned HTML
	* 3) Create the PortalResponse object by passing the previous DOM element as an argumen, it will 
    *    provide an OO view of the PortletResponse and other portal response blocks to update
	* 4) Each portlet response block is the updated using the naming convention "UIPortlet-" + portletId;
	*    and then the script are loaded
	* 5) Then it is each portal block which is updated and the assocaited scripts are evaluated
	*/
	instance.ajaxResponse = function(request){
	  var temp =  document.createElement("div") ;
	  temp.innerHTML =  this.request.responseText ;
	  var responseDiv = eXo.core.DOMUtil.findFirstDescendantByClass(temp, "div", "PortalResponse") ;
	  var response = new PortalResponse(responseDiv) ;
	  //Handle the portlet responses
	  var portletResponses =  response.portletResponses ;
	  if(portletResponses != null) {
	    for(var i = 0; i < portletResponses.length; i++) {
	      var portletResponse = portletResponses[i] ;
	      if(portletResponse.blocksToUpdate == null) {
	        /*
	        * This means that the entire portlet fragment is included in the portletResponse.portletData
	        * and that it does not contain any finer block to update. Hence replace the innerHTML inside the
	        * id="PORTLET-FRAGMENT" block
	        */
	        var parentBlock =  document.getElementById(portletResponse.portletId) ;
	        var target = eXo.core.DOMUtil.findFirstDescendantByClass(parentBlock, "div", "PORTLET-FRAGMENT") ;
	        target.innerHTML = portletResponse.portletData;
	        
	        //update embedded scripts 
	        if(portletResponse.scripts) {
	          if(portletResponse.scripts.length > 0) {
		          for(var k = 0 ; k < portletResponse.scripts.length; k++) {
		            var encodedName = 'script_' + k + '_' +  portletResponse.portletId;
		            appendScriptToHead(encodedName, portletResponse.scripts[k]);
		          }
			      }              
	        }
            
        } else {
	        /*
	        * Else updates each block with the portlet
	        */
	        instance.updateBlocks(portletResponse.blocksToUpdate, portletResponse.portletId) ;
	      }
	    }
	  }
	  if(response.blocksToUpdate == undefined && temp.innerHTML !== "") {
	  	if(confirm(eXo.i18n.I18NMessage.getMessage("SessionTimeout"))) instance.ajaxTimeout(request) ;
	  }
	  //Handle the portal responses
	  instance.updateBlocks(response.blocksToUpdate) ;
		instance.updateHtmlHead(response);
	  instance.executeScript(response.script) ;
	  /**
       * Clears the instance.to timeout if the request takes less time than expected to get response
       * Removes the transparent mask so the UI is available again, with cursor "auto"
       */
	  clearTimeout(instance.to);
//	  eXo.core.UIMaskLayer.removeTransparentMask();
//	  eXo.core.UIMaskLayer.removeMask(eXo.portal.AjaxRequest.maskLayer) ;
	  eXo.core.UIMaskLayer.removeMasks(eXo.portal.AjaxRequest.maskLayer) ;

	  eXo.portal.AjaxRequest.maskLayer = null ;
	  eXo.portal.CurrentRequest = null ;
	} ;
	
	/*
	  * This method is called when doing an AJAX call, it will put the "Loading" image in the
	  * middle of the page for the entire call of the request
	  */
	instance.ajaxLoading = function(request) {
		if (request.isAsynchronize()) return;
		/**
		 * Waits 2 seconds (2000 ms) to display the loading popup
		 * if the response comes before this timeout, the loading popup won't appear at all
		 * Displays a transparent mask with the "wait" cursor to tell the user something is processing
		 * 
		 * Modified by Truong LE to avoid double click problem
		 */		
		 
		Browser = eXo.core.Browser; 
		if(eXo.portal.AjaxRequest.maskLayer == null ){
			eXo.portal.AjaxRequest.maskLayer = eXo.core.UIMaskLayer.createTransparentMask("TOP-CENTER");
		}
		instance.to = setTimeout(function() {
			if(eXo.portal.AjaxRequest.maskLayer != null) {
				eXo.core.UIMaskLayer.showAjaxLoading(eXo.portal.AjaxRequest.maskLayer);			   
			}
		}, 2000);
	};
	
	return instance ;
} ;

/*****************************************************************************************/
/*
* This is the main entry method for every Ajax calls to the eXo Portal
*
* It is simply a dispatcher method that fills some init fields before 
* calling the doRequest() method
*/
function ajaxGet(url, callback) {
  if (!callback) callback = null ;
  doRequest("Get", url, null, callback) ;
} ;

/**
 * Do a POST request in AJAX with given <code>url</code> and <code>queryString</code>.
 * The call is delegated to the doRequest() method with a callback function
 */
function ajaxPost(url, queryString, callback) {
  if (!callback) callback = null ;
  doRequest("POST", url, queryString, callback) ;
} ;

/*
* The doRequest() method takes incoming request from GET and POST calls
* The second argument is the URL to target on the server
* The third argument is the query string object which is created out of
* a form element, this value is not null only when there is a POST request.
*
* 1) An AjaxRequest object is instanciated, it holds the reference to the
*    XHR method
* 2) An HttpResponseHandler object is instantiated and its methods like
*    ajaxResponse, ajaxLoading, ajaxTimeout are associated with the one from
*    the AjaxRequest and will be called by the XHR during the process method 
*/
function doRequest(method, url, queryString, callback) {
  request = new AjaxRequest(method, url, queryString) ;
  handler = new HttpResponseHandler() ;
  request.onSuccess = handler.ajaxResponse ;
  request.onLoading = handler.ajaxLoading ;
  request.onTimeout = handler.ajaxTimeout ;
  request.callBack = callback ;
  eXo.portal.CurrentRequest = request ;
  request.process() ;
  eXo.session.itvDestroy() ;
  if(eXo.session.canKeepState && eXo.session.isOpen  && eXo.env.portal.accessMode == 'private') {
	  eXo.session.itvInit() ;
  }
}	;

/**
 * Abort an ajax request
 * @return
 */
function ajaxAbort() {	
  eXo.core.UIMaskLayer.removeMasks(eXo.portal.AjaxRequest.maskLayer) ;
  eXo.portal.AjaxRequest.maskLayer = null ;	  

  eXo.portal.CurrentRequest.request.abort() ;  
  eXo.portal.CurrentRequest.aborted = true ;
  eXo.portal.CurrentRequest = null ;
} ;

/**
 * Create a ajax request
 * @param {String} url - Url
 * @param {boolean} async - asynchronous or none
 * @return {String} response text if request is not async
 */
function ajaxAsyncGetRequest(url, async) {
	return ajaxRequest("GET", url, async);
}

/**
 * Create an ajax request
 * @param {String} method - GET, POST, etc
 * @param {String} url - Url
 * @param {boolean} async - asynchronous or none
 * @return {String} response text if request is not async
 */
function ajaxRequest(method, url, async, queryString) {
	if(async == undefined) async = true ;
	var request =  eXo.core.Browser.createHttpRequest() ;
  request.open(method, url, async) ;
  request.setRequestHeader("Cache-Control", "max-age=86400") ;
  if(queryString != undefined)
  {
    request.send(queryString) ;
  }
  else
  {
    request.send(null);
  }
  eXo.session.itvDestroy() ;
  if(eXo.session.canKeepState && eXo.session.isOpen && eXo.env.portal.accessMode == 'private') {
    eXo.session.itvInit() ;
  }
	if(!async) return request.responseText ;
}

/**
 * Redirect browser to url
 * @param url
 * @return
 */
function ajaxRedirect(url) {
	url =	url.replace(/&amp;/g, "&") ;
	window.location.href = url ;
}

eXo.portal.AjaxRequest = AjaxRequest.prototype.constructor ;
