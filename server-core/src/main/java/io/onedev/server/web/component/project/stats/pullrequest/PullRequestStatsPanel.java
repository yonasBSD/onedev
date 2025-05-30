package io.onedev.server.web.component.project.stats.pullrequest;

import static io.onedev.server.web.translation.Translation._T;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.model.Project;
import io.onedev.server.model.PullRequest;
import io.onedev.server.model.PullRequest.Status;
import io.onedev.server.search.entity.pullrequest.DiscardedCriteria;
import io.onedev.server.search.entity.pullrequest.MergedCriteria;
import io.onedev.server.search.entity.pullrequest.OpenCriteria;
import io.onedev.server.search.entity.pullrequest.PullRequestQuery;
import io.onedev.server.util.criteria.Criteria;
import io.onedev.server.web.page.project.pullrequests.ProjectPullRequestsPage;

public class PullRequestStatsPanel extends Panel {

	private final IModel<Project> projectModel;
	
	private final IModel<Map<Status, Long>> statsModel;
	
	public PullRequestStatsPanel(String id, IModel<Project> projectModel, IModel<Map<Status, Long>> statsModel) {
		super(id);
		
		this.projectModel = projectModel;
		this.statsModel = statsModel;
	}
	
	private long getTotalCount() {
		return getStats().values().stream().mapToLong(Long::longValue).sum();
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		PageParameters params = ProjectPullRequestsPage.paramsOf(getProject());
		Link<Void> pullRequestsLink = new BookmarkablePageLink<Void>("pullRequests", ProjectPullRequestsPage.class, params);
		pullRequestsLink.add(new Label("label", new LoadableDetachableModel<String>() {

			@Override
			protected String load() {
				return getTotalCount() + " " + _T("pull requests");
			}
			
		}));
		add(pullRequestsLink);
		
		add(new ListView<Map.Entry<Status, Long>>("statuses", 
				new LoadableDetachableModel<List<Map.Entry<Status, Long>>>() {

			@Override
			protected List<Map.Entry<Status, Long>> load() {
				return new ArrayList<>(getStats().entrySet());
			}
			
		}) {

			@Override
			protected void populateItem(ListItem<Map.Entry<Status, Long>> item) {
				Map.Entry<Status, Long> entry = item.getModelObject();
				Criteria<PullRequest> criteria;
				if (entry.getKey() == Status.OPEN) {
					criteria = new OpenCriteria();
				} else if (entry.getKey() == Status.MERGED) {
					criteria = new MergedCriteria();
				} else {
					criteria = new DiscardedCriteria();
				}
				PullRequestQuery query = new PullRequestQuery(criteria);
				PageParameters params = ProjectPullRequestsPage.paramsOf(getProject(), query.toString(), 0);
				Link<Void> statusLink = new BookmarkablePageLink<Void>("link", ProjectPullRequestsPage.class, params);
				String statusName = entry.getKey().toString();
				statusLink.add(new Label("label", entry.getValue() + " " + _T(statusName)));
				
				String cssClass;
				switch (entry.getKey()) {
				case OPEN:
					cssClass = "link-warning";
					break;
				case MERGED:
					cssClass = "link-success";
					break;
				default:
					cssClass = "link-info";
					break;
				}
				statusLink.add(AttributeAppender.append("class", cssClass));
				item.add(statusLink);
			}
			
		});
	}

	@Override
	protected void onConfigure() {
		super.onConfigure();
		setVisible(getTotalCount() != 0);
	}

	@Override
	protected void onDetach() {
		projectModel.detach();
		statsModel.detach();
		super.onDetach();
	}

	private Project getProject() {
		return projectModel.getObject();
	}
	
	private Map<Status, Long> getStats() {
		return statsModel.getObject();
	}

}
