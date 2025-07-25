package io.onedev.server.web.component.commit.list;

import static io.onedev.server.web.translation.Translation._T;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import io.onedev.commons.utils.ExplicitException;
import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.BuildManager;
import io.onedev.server.git.BlobIdent;
import io.onedev.server.git.GitUtils;
import io.onedev.server.git.command.RevListOptions;
import io.onedev.server.git.service.GitService;
import io.onedev.server.git.service.RefFacade;
import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;
import io.onedev.server.search.commit.CommitCriteria;
import io.onedev.server.search.commit.CommitQuery;
import io.onedev.server.search.commit.FuzzyCriteria;
import io.onedev.server.search.commit.MessageCriteria;
import io.onedev.server.search.commit.PathCriteria;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.DateUtils;
import io.onedev.server.util.ProjectAndRevision;
import io.onedev.server.util.patternset.PatternSet;
import io.onedev.server.web.behavior.CommitQueryBehavior;
import io.onedev.server.web.behavior.RunTaskBehavior;
import io.onedev.server.web.component.commit.message.CommitMessagePanel;
import io.onedev.server.web.component.commit.status.CommitStatusLink;
import io.onedev.server.web.component.commit.status.CommitStatusSupport;
import io.onedev.server.web.component.contributorpanel.ContributorPanel;
import io.onedev.server.web.component.floating.FloatingPanel;
import io.onedev.server.web.component.gitsignature.SignatureStatusPanel;
import io.onedev.server.web.component.link.DropdownLink;
import io.onedev.server.web.component.link.ViewStateAwarePageLink;
import io.onedev.server.web.component.link.copytoclipboard.CopyToClipboardLink;
import io.onedev.server.web.component.savedquery.SavedQueriesClosed;
import io.onedev.server.web.component.savedquery.SavedQueriesOpened;
import io.onedev.server.web.component.user.contributoravatars.ContributorAvatars;
import io.onedev.server.web.page.project.blob.ProjectBlobPage;
import io.onedev.server.web.page.project.commits.CommitDetailPage;
import io.onedev.server.web.page.project.compare.RevisionComparePage;
import io.onedev.server.web.util.QuerySaveSupport;

public abstract class CommitListPanel extends Panel {

	private static final Logger logger = LoggerFactory.getLogger(CommitListPanel.class);
	
	private static final int COMMITS_PER_PAGE = 50;
	
	private static final int MAX_PAGES = 20;
	
	private final IModel<String> queryStringModel;
	
	private final IModel<CommitQuery> queryModel = new LoadableDetachableModel<CommitQuery>() {

		@Override
		protected CommitQuery load() {
			return parse(queryStringModel.getObject(), getBaseQuery());
		}
		
	};
	
	private final IModel<Commits> commitsModel = new LoadableDetachableModel<Commits>() {

		private List<RevCommit> separateByDate(List<RevCommit> commits) {
			List<RevCommit> separated = new ArrayList<>();
			LocalDate groupDate = null;
			for (RevCommit commit: commits) {
				var commitDate = DateUtils.toLocalDate(commit.getCommitterIdent().getWhen());
				if (groupDate == null || commitDate.toEpochDay() != groupDate.toEpochDay()) {
					groupDate = commitDate;
					separated.add(null);
				} 
				separated.add(commit);
			}
			return separated;
		}
		
		@Override
		protected Commits load() {
			CommitQuery query = queryModel.getObject();
			Commits commits = new Commits();
			List<String> commitHashes;
			if (query != null) {
				try {
					RevListOptions options = new RevListOptions();
					options.ignoreCase(true);
					
					if (page > MAX_PAGES)
						throw new ExplicitException("Page should be no more than " + MAX_PAGES);
					
					options.count(page * COMMITS_PER_PAGE);
					
					query.fill(getProject(), options);
					
					if (options.revisions().isEmpty() && getCompareWith() != null)
						options.revisions(Lists.newArrayList(getCompareWith()));
					
					commitHashes = getGitService().revList(getProject(), options);
				} catch (Exception e) {
					if (e.getMessage() != null)
						error(e.getMessage());
					else
						error(_T("Error calculating commits: check log for details"));
					commitHashes = new ArrayList<>();
					logger.error("Error calculating commits: ", e);
				}
			} else {
				commitHashes = new ArrayList<>();
			}
			
			commits.hasMore = (commitHashes.size() == page * COMMITS_PER_PAGE);
			
			int lastMaxCount = Math.min((page - 1) * COMMITS_PER_PAGE, commitHashes.size());
			
			List<ObjectId> commitIds = new ArrayList<>();
			for (int i=0; i<lastMaxCount; i++) 
				commitIds.add(ObjectId.fromString(commitHashes.get(i)));
			
			commits.last = getGitService().getCommits(getProject(), commitIds);
			sort(commits.last, 0);
			commits.current = new ArrayList<>(commits.last);
			
			commitIds.clear();
			for (int i=lastMaxCount; i<commitHashes.size(); i++)
				commitIds.add(ObjectId.fromString(commitHashes.get(i)));
			
			for (RevCommit commit: getGitService().getCommits(getProject(), commitIds))
				commits.current.add(commit);
			
			sort(commits.current, lastMaxCount);
			commits.last = separateByDate(commits.last);
			commits.current = separateByDate(commits.current);
			
			return commits;
		}
		
	};
	
	private final IModel<Map<String, List<String>>> labelsModel = new LoadableDetachableModel<Map<String, List<String>>>() {
		
		@Override
		protected Map<String, List<String>> load() {
			Map<String, List<String>> labels = new HashMap<>();
			List<RefFacade> refInfos = getProject().getBranchRefs();
			refInfos.addAll(getProject().getTagRefs());
			for (RefFacade ref: refInfos) {
				if (ref.getPeeledObj() instanceof RevCommit) {
					RevCommit commit = (RevCommit) ref.getPeeledObj();
					List<String> commitLabels = labels.get(commit.name());
					if (commitLabels == null) {
						commitLabels = new ArrayList<>();
						labels.put(commit.name(), commitLabels);
					}
					commitLabels.add(Repository.shortenRefName(ref.getName()));
				}
			}
			return labels;
		}
	};
	
	private int page = 1;
	
	private transient Collection<ObjectId> commitIdsToQueryStatus;
	
	private WebMarkupContainer body;
	
	private WebMarkupContainer foot;
	
	private RepeatingView commitsView;
	
	private Component saveQueryLink;
	
	private TextField<String> queryInput;

	private boolean querySubmitted = true;
	
	public CommitListPanel(String id, IModel<String> queryModel) {
		super(id);
		this.queryStringModel = queryModel;
	}
	
	@Override
	protected void onDetach() {
		queryStringModel.detach();
		queryModel.detach();
		commitsModel.detach();
		labelsModel.detach();
		super.onDetach();
	}
	
	protected abstract Project getProject();
	
	@Nullable
	protected CommitStatusSupport getStatusSupport() {
		return null;
	}
	
	@Nullable
	protected String getCompareWith() {
		return null;
	}

	protected CommitQuery getBaseQuery() {
		return new CommitQuery(new ArrayList<>());
	}

	@Nullable
	protected QuerySaveSupport getQuerySaveSupport() {
		return null;
	}

	@Nullable
	private CommitQuery parse(@Nullable String queryString, CommitQuery baseQuery) {
		CommitQuery parsedQuery;
		try {
			parsedQuery = CommitQuery.parse(getProject(), queryString, true);
		} catch (Exception e) {
			getFeedbackMessages().clear();
			if (e instanceof ExplicitException) {
				error(e.getMessage());
				return null;
			} else {
				info(_T("Performing fuzzy query. Enclosing search text with '~' to add more conditions, for instance: ~text to search~ author(robin)"));
				parsedQuery = new CommitQuery(Lists.newArrayList(new FuzzyCriteria(Lists.newArrayList(queryString))));
			}
		}
		return CommitQuery.merge(baseQuery, parsedQuery);
	}
	
	private void doQuery(AjaxRequestTarget target) {
		page = 1;
		target.add(body);
		target.add(foot);
		querySubmitted = true;
		target.appendJavaScript(renderCommitGraph());
		if (SecurityUtils.getAuthUser() != null && getQuerySaveSupport() != null)
			target.add(saveQueryLink);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();

		add(new AjaxLink<Void>("showSavedQueries") {

			@Override
			public void onEvent(IEvent<?> event) {
				super.onEvent(event);
				if (event.getPayload() instanceof SavedQueriesClosed) {
					((SavedQueriesClosed) event.getPayload()).getHandler().add(this);
				}
			}
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getQuerySaveSupport() != null && !getQuerySaveSupport().isSavedQueriesVisible());
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				send(getPage(), Broadcast.BREADTH, new SavedQueriesOpened(target));
				target.add(this);
			}
			
		}.setOutputMarkupPlaceholderTag(true));
		
		add(saveQueryLink = new AjaxLink<Void>("saveQuery") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setEnabled(querySubmitted && queryModel.getObject() != null);
				setVisible(SecurityUtils.getAuthUser() != null && getQuerySaveSupport() != null);
			}

			@Override
			protected void onComponentTag(ComponentTag tag) {
				super.onComponentTag(tag);
				configure();
				if (!isEnabled()) 
					tag.append("class", "disabled", " ");
				if (!querySubmitted)
					tag.put("data-tippy-content", _T("Query not submitted"));
				else if (queryModel.getObject() == null)
					tag.put("data-tippy-content", _T("Can not save malformed query"));
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				getQuerySaveSupport().onSaveQuery(target, queryModel.getObject().toString());
			}		
			
		}.setOutputMarkupPlaceholderTag(true));
		
		add(new DropdownLink("filter") {
			@Override
			protected Component newContent(String id, FloatingPanel dropdown) {
				return new CommitFilterPanel(id, new IModel<CommitQuery>() {
					@Override
					public void detach() {
					}
					@Override
					public CommitQuery getObject() {
						var query = parse(queryStringModel.getObject(), new CommitQuery(new ArrayList<>()));
						return query!=null? query : new CommitQuery(new ArrayList<>());
					}
					@Override
					public void setObject(CommitQuery object) {
						CommitListPanel.this.getFeedbackMessages().clear();
						queryStringModel.setObject(object.toString());
						var target = RequestCycle.get().find(AjaxRequestTarget.class);
						target.add(queryInput);
						doQuery(target);
					}
				}) {

					@Override
					protected Project getProject() {
						return CommitListPanel.this.getProject();
					}
					
				};
			}
		});

		queryInput = new TextField<String>("input", queryStringModel);
		queryInput.add(new CommitQueryBehavior(new AbstractReadOnlyModel<Project>() {

			@Override
			public Project getObject() {
				return getProject();
			}
			
		}, true) {
			
			@Override
			protected void onInput(AjaxRequestTarget target, String inputContent) {
				CommitListPanel.this.getFeedbackMessages().clear();
				querySubmitted = StringUtils.trimToEmpty(queryStringModel.getObject())
						.equals(StringUtils.trimToEmpty(inputContent));
				target.add(saveQueryLink);
			}
			
		});
		
		queryInput.add(new AjaxFormComponentUpdatingBehavior("clear") {
			
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				doQuery(target);
			}
			
		});
		
		Form<?> queryForm = new Form<Void>("query");
		queryForm.add(queryInput);
		queryForm.add(new AjaxButton("submit") {

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				super.onSubmit(target, form);
				CommitListPanel.this.getFeedbackMessages().clear();
				doQuery(target);
			}
			
		});
		add(queryForm);
		
		body = new WebMarkupContainer("body") {
			
			@Override
			protected void onBeforeRender() {
				addOrReplace(commitsView = newCommitsView());
				super.onBeforeRender();
			}

		};
		body.setOutputMarkupId(true);
		add(body);
		
		FencedFeedbackPanel feedback;
		body.add(feedback = new FencedFeedbackPanel("feedback", this));
		
		body.add(new WebMarkupContainer("noCommits") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!feedback.anyErrorMessage() && commitsModel.getObject().current.isEmpty());
			}
			
		});		
				
		add(foot = new WebMarkupContainer("foot"));
		foot.setOutputMarkupId(true);
		
		foot.add(new AjaxLink<Void>("more") {

			private RunTaskBehavior taskBehavior;
			
			@Override
			protected void onInitialize() {
				super.onInitialize();
				
				add(taskBehavior = new RunTaskBehavior() {

					@Override
					protected void runTask(AjaxRequestTarget target) {
						page++;
						
						Commits commits = commitsModel.getObject();
						int commitIndex = 0;
						int lastCommitIndex = 0;
						for (int i=0; i<commits.last.size(); i++) {
							RevCommit lastCommit = commits.last.get(i);
							RevCommit currentCommit = commits.current.get(i);
							if (lastCommit == null) {
								if (currentCommit == null) {
									if (!commits.last.get(i+1).name().equals(commits.current.get(i+1).name())) 
										replaceItem(target, i);
								} else {
									addCommitClass(replaceItem(target, i), commitIndex);
								}
							} else {
								if (currentCommit == null) {
									replaceItem(target, i);
								} else if (commitIndex != lastCommitIndex 
										|| !lastCommit.name().equals(currentCommit.name())){
									addCommitClass(replaceItem(target, i), commitIndex);
								}						
							}
							if (lastCommit != null)
								lastCommitIndex++;
							if (currentCommit != null)
								commitIndex++;
						}

						StringBuilder builder = new StringBuilder();
						for (int i=commits.last.size(); i<commits.current.size(); i++) {
							Component item = newCommitItem(commitsView.newChildId(), i);
							if (commits.current.get(i) != null)
								addCommitClass(item, commitIndex++);
							commitsView.add(item);
							target.add(item);
							builder.append(String.format("$('#%s>.list').append(\"<li id='%s'></li>\");", 
									body.getMarkupId(), item.getMarkupId()));
						}
						target.prependJavaScript(builder);
						target.add(foot);
						target.focusComponent(null);
						target.appendJavaScript(renderCommitGraph());
						
						getProject().cacheCommitStatuses(getBuildManager().queryStatus(getProject(), getCommitIdsToQueryStatus()));						
					}
					
				});
			}

			@Override
			public void onClick(AjaxRequestTarget target) {
				taskBehavior.requestRun(target);
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(commitsModel.getObject().hasMore && page < MAX_PAGES);
			}
			
		});
		
		foot.add(new WebMarkupContainer("tooMany") {
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(page >= MAX_PAGES);
			}
			
		});
		
		setOutputMarkupId(true);
	}
	
	private BuildManager getBuildManager() {
		return OneDev.getInstance(BuildManager.class);
	}
	
	private Collection<ObjectId> getCommitIdsToQueryStatus() {
		if (commitIdsToQueryStatus == null)
			commitIdsToQueryStatus = new HashSet<>();
		return commitIdsToQueryStatus;
	}
	
	private RepeatingView newCommitsView() {
		RepeatingView commitsView = new RepeatingView("commits");
		commitsView.setOutputMarkupId(true);
		
		int commitIndex = 0;
		List<RevCommit> commits = commitsModel.getObject().current;
		for (int i=0; i<commits.size(); i++) {
			Component item = newCommitItem(commitsView.newChildId(), i);
			if (commits.get(i) != null)
				addCommitClass(item, commitIndex++);
			commitsView.add(item);
		}
		getProject().cacheCommitStatuses(getBuildManager().queryStatus(getProject(), getCommitIdsToQueryStatus()));
		return commitsView;
	}

	@SuppressWarnings("deprecation")
	private Component replaceItem(AjaxRequestTarget target, int index) {
		Component item = commitsView.get(index);
		Component newItem = newCommitItem(item.getId(), index);
		item.replaceWith(newItem);
		target.add(newItem);
		return newItem;
	}
	
	private void addCommitClass(Component item, int commitIndex) {
		item.add(AttributeAppender.append("class", " commit-item-" + commitIndex));
	}
	
	private Component newCommitItem(String itemId, int index) {
		List<RevCommit> current = commitsModel.getObject().current;
		RevCommit commit = current.get(index);
		
		Fragment item;
		if (commit != null) {
			item = new Fragment(itemId, "commitFrag", this);
			item.add(new ContributorAvatars("avatar", commit.getAuthorIdent(), commit.getCommitterIdent()));

			item.add(new CommitMessagePanel("message", new LoadableDetachableModel<RevCommit>() {

				@Override
				protected RevCommit load() {
					return commitsModel.getObject().current.get(index);
				}
				
			}, new LoadableDetachableModel<>() {

				@Override
				protected List<Pattern> load() {
					List<Pattern> patterns = new ArrayList<>();
					for (CommitCriteria criteria : queryModel.getObject().getCriterias()) {
						if (criteria instanceof MessageCriteria) {
							for (String value : ((MessageCriteria) criteria).getValues())
								patterns.add(Pattern.compile(value, Pattern.CASE_INSENSITIVE));
						} else if (criteria instanceof FuzzyCriteria) {
							for (String value : ((FuzzyCriteria) criteria).getValues()) {
								if (getProject().getObjectId(value, false) == null) {
									patterns.add(Pattern.compile(value.replace(" ", ".*"), Pattern.CASE_INSENSITIVE));
								}
							}
						}
					}
					return patterns;
				}

			}) {

				@Override
				protected Project getProject() {
					return CommitListPanel.this.getProject();
				}
				
			});

			RepeatingView labelsView = new RepeatingView("labels");

			List<String> commitLabels = labelsModel.getObject().get(commit.name());
			if (commitLabels == null)
				commitLabels = new ArrayList<>();
			for (String label: commitLabels) {
				WebMarkupContainer container = new WebMarkupContainer(labelsView.newChildId());
				container.add(new Label("label", label));
				labelsView.add(container);
			}
			item.add(labelsView);
			
			item.add(new ContributorPanel("contribution", commit.getAuthorIdent(), commit.getCommitterIdent()));
			
			/*
			 * If we query a single definitive path, let's record it to be used for 
			 * diff comparison and code browsing  
			 */
			String path = null;
			
			for (CommitCriteria criteria: queryModel.getObject().getCriterias()) {
				if (criteria instanceof PathCriteria) {
					for (String value: ((PathCriteria) criteria).getValues()) {
						if (value.contains("*") || path != null) {
							path = null;
							break;
						} else {
							path = value;
						}
					}
				}
			}

			BlobIdent blobIdent;
			if (path != null) {
				blobIdent = new BlobIdent(commit.name(), path, null);
			} else {
				blobIdent = new BlobIdent(commit.name(), null, FileMode.TREE.getBits());
			}
			ProjectBlobPage.State browseState = new ProjectBlobPage.State(blobIdent);
			PageParameters params = ProjectBlobPage.paramsOf(getProject(), browseState);
			item.add(new ViewStateAwarePageLink<Void>("browseCode", ProjectBlobPage.class, params));
			
			if (getCompareWith() != null) {
				RevisionComparePage.State compareState = new RevisionComparePage.State();
				compareState.leftSide = new ProjectAndRevision(getProject(), commit.name());
				compareState.rightSide = new ProjectAndRevision(getProject(), getCompareWith());
				if (path != null)
					compareState.pathFilter = PatternSet.quoteIfNecessary(path);
				compareState.tabPanel = RevisionComparePage.TabPanel.FILE_CHANGES;
				
				params = RevisionComparePage.paramsOf(getProject(), compareState);
				item.add(new ViewStateAwarePageLink<Void>("compare", RevisionComparePage.class, params));
			} else {
				item.add(new WebMarkupContainer("compare").setVisible(false));
			}

			CommitDetailPage.State commitState = new CommitDetailPage.State();
			commitState.revision = commit.name();
			params = CommitDetailPage.paramsOf(getProject(), commitState);
			Link<Void> hashLink = new ViewStateAwarePageLink<Void>("hashLink", CommitDetailPage.class, params);
			item.add(hashLink);
			hashLink.add(new Label("hash", GitUtils.abbreviateSHA(commit.name())));
			item.add(new CopyToClipboardLink("copyHash", Model.of(commit.name())));
			
			getCommitIdsToQueryStatus().add(commit.copy());
			
			item.add(new SignatureStatusPanel("signature") {
				
				@Override
				protected RevObject getRevObject() {
					return commitsModel.getObject().current.get(index);
				}
				
			});
			
			if (getStatusSupport() != null) {
				item.add(new CommitStatusLink("buildStatus", commit.copy(), getStatusSupport().getRefName()) {

					@Override
					protected Project getProject() {
						return CommitListPanel.this.getProject();
					}

					@Override
					protected PullRequest getPullRequest() {
						return null;
					}

				});
			} else {
				item.add(new WebMarkupContainer("buildStatus").setVisible(false));				
			}
			item.add(AttributeAppender.append("class", "commit"));
		} else {
			item = new Fragment(itemId, "dateFrag", this);
			item.add(new Label("date", DateUtils.formatDate(current.get(index+1).getCommitterIdent().getWhen())));
			item.add(AttributeAppender.append("class", "date"));
		}
		item.setOutputMarkupId(true);
		
		return item;
	}
	
	@Override
	protected void onBeforeRender() {
		page = 1;
		super.onBeforeRender();
	}

	private String renderCommitGraph() {
		String jsonOfCommits = asJSON(commitsModel.getObject().current);
		return String.format("onedev.server.commitList.renderGraph('%s', %s);", body.getMarkupId(), jsonOfCommits);
	}
	
	private void sort(List<RevCommit> commits, int from) {
		final Map<String, Long> hash2index = new HashMap<>();
		Map<String, RevCommit> hash2commit = new HashMap<>();
		for (int i=0; i<commits.size(); i++) {
			RevCommit commit = commits.get(i);
			hash2index.put(commit.name(), 1L*i*commits.size());
			hash2commit.put(commit.name(), commit);
		}

		Stack<RevCommit> stack = new Stack<>();
		
		for (int i=commits.size()-1; i>=from; i--)
			stack.push(commits.get(i));

		// commits are nearly ordered, so this should be fast
		while (!stack.isEmpty()) {
			RevCommit commit = stack.pop();
			long commitIndex = hash2index.get(commit.name());
			int count = 1;
			for (RevCommit parent: commit.getParents()) {
				String parentHash = parent.name();
				Long parentIndex = hash2index.get(parentHash);
				if (parentIndex != null && parentIndex.longValue()<commitIndex) {
					stack.push(hash2commit.get(parentHash));
					hash2index.put(parentHash, commitIndex+(count++));
				}
			}
		}
		
		commits.sort((o1, o2) -> {
			long value = hash2index.get(o1.name()) - hash2index.get(o2.name());
			if (value < 0)
				return -1;
			else if (value > 0)
				return 1;
			else
				return 0;
		});
	}
	
	private String asJSON(List<RevCommit> commits) {
		Map<String, Integer> hash2index = new HashMap<>();
		int commitIndex = 0;
		for (int i=0; i<commits.size(); i++) { 
			RevCommit commit = commits.get(i);
			if (commit != null)
				hash2index.put(commit.name(), commitIndex++);
		}
		List<List<Integer>> commitIndexes = new ArrayList<>();
		for (RevCommit commit: commits) {
			if (commit != null) {
				List<Integer> parentIndexes = new ArrayList<>();
				for (RevCommit parent: commit.getParents()) {
					Integer parentIndex = hash2index.get(parent.name());
					if (parentIndex != null)
						parentIndexes.add(parentIndex);
				}
				commitIndexes.add(parentIndexes);
			}
		}
		try {
			return OneDev.getInstance(ObjectMapper.class).writeValueAsString(commitIndexes);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
		
	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(JavaScriptHeaderItem.forReference(new CommitListResourceReference()));
		
		String jsonOfCommits = asJSON(commitsModel.getObject().current);
		String script = String.format("onedev.server.commitList.onDomReady('%s', %s);", body.getMarkupId(), jsonOfCommits);		
		response.render(OnDomReadyHeaderItem.forScript(script));
	}
	
	private GitService getGitService() {
		return OneDev.getInstance(GitService.class);
	}
	
	/*
	 * We do not use "--date-order", "--topo-order" or "--author-order" option of git log to 
	 * retrieve commits as they can be slow. However use the default log ordering has the 
	 * possibility of returning parent before child which can corrupt the commit lane. To 
	 * solve this issue, we sort the commits in memory so that parent always comes after 
	 * child. When more commits are loaded via "more" button, it is possible that some 
	 * commits displayed previously can be parent of commits loaded lately and should be 
	 * moved. To work around this issue, we calculate exact order of commits being displayed 
	 * as "last", and calculate commits will be displayed as "current". Then we compare them 
	 * to see which commit item in the page should be replaced, and which should be added. 
	 *  
	 * @author robin
	 *
	 */
	private static class Commits {
		
		List<RevCommit> last;
		
		List<RevCommit> current;
		
		boolean hasMore;
		
	}	
}
