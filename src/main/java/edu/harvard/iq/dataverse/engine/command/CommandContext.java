package edu.harvard.iq.dataverse.engine.command;

import edu.harvard.iq.dataverse.DOIDataCiteServiceBean;
import edu.harvard.iq.dataverse.DOIEZIdServiceBean;
import edu.harvard.iq.dataverse.HandlenetServiceBean;
import edu.harvard.iq.dataverse.PidServiceBean;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetLinkingServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DataverseFacetServiceBean;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.DataverseLinkingServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.FeaturedDataverseServiceBean;
import edu.harvard.iq.dataverse.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.GuestbookServiceBean;
import edu.harvard.iq.dataverse.search.IndexServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.search.SearchServiceBean;
import edu.harvard.iq.dataverse.TemplateServiceBean;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.engine.DataverseEngine;
import edu.harvard.iq.dataverse.search.SolrIndexServiceBean;
import edu.harvard.iq.dataverse.search.savedsearch.SavedSearchServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import javax.persistence.EntityManager;

/**
 * An interface for accessing Dataverse's resources, user info etc. Used by the
 * {@link Command} implementations to perform their intended actions.
 * 
 * @author michael
 */
public interface CommandContext {
    
	public EntityManager em();
	
	public DataverseEngine engine();
	
	public DvObjectServiceBean dvObjects();
	
	public DatasetServiceBean datasets();
	
	public DataverseServiceBean dataverses();
	
	public DataverseRoleServiceBean roles();
	
	public BuiltinUserServiceBean builtinUsers();
	
	public IndexServiceBean index();

    public SolrIndexServiceBean solrIndex();
	
	public SearchServiceBean search();
	
	public PermissionServiceBean permissions();
    
    public RoleAssigneeServiceBean roleAssignees();
	
	public DataverseFacetServiceBean facets(); 
        
    public FeaturedDataverseServiceBean featuredDataverses();       
    
    public DataFileServiceBean files(); 
    
    public TemplateServiceBean templates();
    
    public SavedSearchServiceBean savedSearches();
    
    public DataverseFieldTypeInputLevelServiceBean fieldTypeInputLevels();
               
    public DOIEZIdServiceBean doiEZId();
    
    public DOIDataCiteServiceBean doiDataCite();
    
    public HandlenetServiceBean handleNet();

    public PidServiceBean pidWebservice();

    public GuestbookServiceBean guestbooks();
    
    public GuestbookResponseServiceBean responses();
    
    public DataverseLinkingServiceBean dvLinking();
    
    public DatasetLinkingServiceBean dsLinking();
    
    public SettingsServiceBean settings();       
    
    public ExplicitGroupServiceBean explicitGroups();
    
    public UserNotificationServiceBean notifications();
    
    public AuthenticationServiceBean authentication();
}
