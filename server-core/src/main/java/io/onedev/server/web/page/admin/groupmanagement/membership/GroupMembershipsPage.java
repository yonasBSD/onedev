package io.onedev.server.web.page.admin.groupmanagement.membership;

import static io.onedev.server.web.translation.Translation._T;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import com.google.common.collect.Sets;

import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.MembershipManager;
import io.onedev.server.entitymanager.UserManager;
import io.onedev.server.model.EmailAddress;
import io.onedev.server.model.Membership;
import io.onedev.server.model.User;
import io.onedev.server.persistence.TransactionManager;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.Similarities;
import io.onedev.server.util.facade.UserCache;
import io.onedev.server.web.WebConstants;
import io.onedev.server.web.behavior.OnTypingDoneBehavior;
import io.onedev.server.web.component.EmailAddressVerificationStatusBadge;
import io.onedev.server.web.component.datatable.DefaultDataTable;
import io.onedev.server.web.component.datatable.selectioncolumn.SelectionColumn;
import io.onedev.server.web.component.floating.FloatingPanel;
import io.onedev.server.web.component.menu.MenuItem;
import io.onedev.server.web.component.menu.MenuLink;
import io.onedev.server.web.component.modal.confirm.ConfirmModalPanel;
import io.onedev.server.web.component.select2.Response;
import io.onedev.server.web.component.select2.ResponseFiller;
import io.onedev.server.web.component.select2.SelectToActChoice;
import io.onedev.server.web.component.user.UserAvatar;
import io.onedev.server.web.component.user.choice.AbstractUserChoiceProvider;
import io.onedev.server.web.component.user.choice.UserChoiceResourceReference;
import io.onedev.server.web.page.admin.groupmanagement.GroupPage;
import io.onedev.server.web.page.user.basicsetting.UserBasicSettingPage;

public class GroupMembershipsPage extends GroupPage {

	private String query;
	
	private DataTable<Membership, Void> membershipsTable;
	
	private SortableDataProvider<Membership, Void> dataProvider ;	
	
	private SelectionColumn<Membership, Void> selectionColumn;
	
	public GroupMembershipsPage(PageParameters params) {
		super(params);
	}

	private EntityCriteria<Membership> getCriteria() {
		EntityCriteria<Membership> criteria = EntityCriteria.of(Membership.class);
		if (query != null) {
			criteria.createCriteria("user").add(Restrictions.or(
					Restrictions.ilike(User.PROP_NAME, query, MatchMode.ANYWHERE), 
					Restrictions.ilike(User.PROP_FULL_NAME, query, MatchMode.ANYWHERE))); 
		} else {
			criteria.setCacheable(true);
		}
		criteria.add(Restrictions.eq("group", getGroup()));
		return criteria;
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		TextField<String> searchField;
		
		add(searchField = new TextField<String>("filterUsers", Model.of(query)));
		searchField.add(new OnTypingDoneBehavior(100) {

			@Override
			protected void onTypingDone(AjaxRequestTarget target) {
				query = searchField.getInput();
				if (StringUtils.isBlank(query))
					query = null;
				target.add(membershipsTable);
				selectionColumn.getSelections().clear();
			}
			
		});
		
		add(new SelectToActChoice<User>("addNew", new AbstractUserChoiceProvider() {

			@Override
			public void query(String term, int page, Response<User> response) {
				UserCache cache = OneDev.getInstance(UserManager.class).cloneCache();
				
				List<User> users = new ArrayList<>(cache.getUsers());
				users.removeAll(getGroup().getMembers());
				users.sort(cache.comparingDisplayName(Sets.newHashSet()));
				
				users = new Similarities<User>(users) {

					@Override
					public double getSimilarScore(User object) {
						return cache.getSimilarScore(object, term);
					}
					
				};
				
				new ResponseFiller<>(response).fill(users, page, WebConstants.PAGE_SIZE);
			}

		}) {

			@Override
			protected void onInitialize() {
				super.onInitialize();
				
				getSettings().setPlaceholder(_T("Add user to group..."));
				getSettings().setFormatResult("onedev.server.userChoiceFormatter.formatResult");
				getSettings().setFormatSelection("onedev.server.userChoiceFormatter.formatSelection");
				getSettings().setEscapeMarkup("onedev.server.userChoiceFormatter.escapeMarkup");
			}
			
			@Override
			protected void onSelect(AjaxRequestTarget target, User selection) {
				Membership membership = new Membership();
				membership.setGroup(getGroup());
				var user = getUserManager().load(selection.getId());
				membership.setUser(user);
				getMembershipManager().create(membership);
				getAuditManager().audit(null, "added user \"" + user.getName() + "\" to group \"" + getGroup().getName() + "\"", null, null);
				target.add(membershipsTable);
				selectionColumn.getSelections().clear();
				Session.get().success(_T("User added to group"));
			}
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(SecurityUtils.isAdministrator());
			}

			@Override
			public void renderHead(IHeaderResponse response) {
				super.renderHead(response);
				
				response.render(JavaScriptHeaderItem.forReference(new UserChoiceResourceReference()));
			}
			
		});			
		
		add(new MenuLink("delete") {

			@Override
			protected List<MenuItem> getMenuItems(FloatingPanel dropdown) {
				List<MenuItem> menuItems = new ArrayList<>();
				
				menuItems.add(new MenuItem() {

					@Override
					public String getLabel() {
						return _T("Remove Selected Users from Group");
					}

					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								new ConfirmModalPanel(target) {
									
									@Override
									protected void onConfirm(AjaxRequestTarget target) {
										getTransactionManager().run(() -> {
											Collection<Membership> memberships = new ArrayList<>();
											for (IModel<Membership> each: selectionColumn.getSelections())
												memberships.add(each.getObject());
											getMembershipManager().delete(memberships);
											for (var membership: memberships) {
												var user = membership.getUser();
												getAuditManager().audit(null, "removed user \"" + user.getName() + "\" from group \"" + getGroup().getName() + "\"", null, null);
											}
											selectionColumn.getSelections().clear();
											target.add(membershipsTable);
										});
									}
									
									@Override
									protected String getConfirmMessage() {
										return _T("Type <code>yes</code> below to remove selected users from group");
									}
									
									@Override
									protected String getConfirmInput() {
										return "yes";
									}
									
								};
								
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(!selectionColumn.getSelections().isEmpty());
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("Please select users to remove from group"));
								}
							}
							
						};
						
					}
					
				});
				
				menuItems.add(new MenuItem() {

					@Override
					public String getLabel() {
						return _T("Remove All Queried Users from Group");
					}
					
					@Override
					public WebMarkupContainer newLink(String id) {
						return new AjaxLink<Void>(id) {

							@SuppressWarnings("unchecked")
							@Override
							public void onClick(AjaxRequestTarget target) {
								dropdown.close();
								
								new ConfirmModalPanel(target) {
									
									@Override
									protected void onConfirm(AjaxRequestTarget target) {
										getTransactionManager().run(() -> {
											Collection<Membership> memberships = new ArrayList<>();
											for (Iterator<Membership> it = (Iterator<Membership>) dataProvider.iterator(0, membershipsTable.getItemCount()); it.hasNext();) 
												memberships.add(it.next());
											getMembershipManager().delete(memberships);
											for (var membership: memberships) {
												var user = membership.getUser();
												getAuditManager().audit(null, "removed user \"" + user.getName() + "\" from group \"" + getGroup().getName() + "\"", null, null);
											}
											selectionColumn.getSelections().clear();
											target.add(membershipsTable);
										});
									}
									
									@Override
									protected String getConfirmMessage() {
										return _T("Type <code>yes</code> below to remove all queried users from group");
									}
									
									@Override
									protected String getConfirmInput() {
										return "yes";
									}
									
								};
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();
								setEnabled(membershipsTable.getItemCount() != 0);
							}
							
							@Override
							protected void onComponentTag(ComponentTag tag) {
								super.onComponentTag(tag);
								configure();
								if (!isEnabled()) {
									tag.put("disabled", "disabled");
									tag.put("data-tippy-content", _T("No users to remove from group"));
								}
							}
							
						};
					}
					
				});
				
				return menuItems;
			}
			
		});
		
		List<IColumn<Membership, Void>> columns = new ArrayList<>();
		
		columns.add(selectionColumn = new SelectionColumn<Membership, Void>());
		
		columns.add(new AbstractColumn<Membership, Void>(Model.of(_T("Name"))) {

			@Override
			public void populateItem(Item<ICellPopulator<Membership>> cellItem, String componentId,
					IModel<Membership> rowModel) {
				User user = rowModel.getObject().getUser();
				Fragment fragment = new Fragment(componentId, "nameFrag", GroupMembershipsPage.this);
				Link<Void> link = new BookmarkablePageLink<Void>("link", UserBasicSettingPage.class, 
						UserBasicSettingPage.paramsOf(user));
				link.add(new UserAvatar("avatar", user));
				link.add(new Label("name", user.getDisplayName()));
				fragment.add(link);
				cellItem.add(fragment);
			}
		});
		
		columns.add(new AbstractColumn<Membership, Void>(Model.of(_T("Primary Email"))) {

			@Override
			public void populateItem(Item<ICellPopulator<Membership>> cellItem, String componentId,
					IModel<Membership> rowModel) {
				EmailAddress emailAddress = rowModel.getObject().getUser().getPrimaryEmailAddress();
				if (emailAddress != null) {
					Fragment fragment = new Fragment(componentId, "emailFrag", GroupMembershipsPage.this);
					fragment.add(new Label("emailAddress", emailAddress.getValue()));
					fragment.add(new EmailAddressVerificationStatusBadge(
							"verificationStatus", Model.of(emailAddress)));
					cellItem.add(fragment);
				} else {
					cellItem.add(new Label(componentId, "<i>" + _T("Not specified") + "</i>").setEscapeModelStrings(false));
				}
			}
			
		});
		
		dataProvider = new SortableDataProvider<Membership, Void>() {

			@Override
			public Iterator<? extends Membership> iterator(long first, long count) {
				EntityCriteria<Membership> criteria = getCriteria();
				criteria.addOrder(Order.desc("id"));
				return OneDev.getInstance(MembershipManager.class).query(criteria, (int)first, 
						(int)count).iterator();
			}

			@Override
			public long size() {
				return OneDev.getInstance(MembershipManager.class).count(getCriteria());
			}

			@Override
			public IModel<Membership> model(Membership object) {
				Long id = object.getId();
				return new LoadableDetachableModel<Membership>() {

					@Override
					protected Membership load() {
						return OneDev.getInstance(MembershipManager.class).load(id);
					}
					
				};
			}
		};
		
		add(membershipsTable = new DefaultDataTable<Membership, Void>("memberships", columns, dataProvider, 
				WebConstants.PAGE_SIZE, null));
	}

	private TransactionManager getTransactionManager() {
		return OneDev.getInstance(TransactionManager.class);
	}

	private MembershipManager getMembershipManager() {
		return OneDev.getInstance(MembershipManager.class);
	}

	private UserManager getUserManager() {
		return OneDev.getInstance(UserManager.class);
	}

}
