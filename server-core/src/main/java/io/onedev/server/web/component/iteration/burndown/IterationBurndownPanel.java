package io.onedev.server.web.component.iteration.burndown;

import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.model.Issue;
import io.onedev.server.model.IssueSchedule;
import io.onedev.server.model.Iteration;
import io.onedev.server.model.support.administration.GlobalIssueSetting;
import io.onedev.server.model.support.issue.StateSpec;
import io.onedev.server.model.support.issue.field.spec.WorkingPeriodField;
import io.onedev.server.web.component.chart.line.Line;
import io.onedev.server.web.component.chart.line.LineChartPanel;
import io.onedev.server.web.component.chart.line.LineSeries;
import io.onedev.server.xodus.IssueInfoManager;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.GenericPanel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;

import static io.onedev.server.util.DateUtils.toLocalDate;
import static io.onedev.server.web.component.iteration.burndown.BurndownIndicators.*;

public class IterationBurndownPanel extends GenericPanel<Iteration> {

	private static final int MAX_DAYS = 365;
	
	private final String indicator;
	
	public IterationBurndownPanel(String id, IModel<Iteration> iterationModel, @Nullable String indicator) {
		super(id, iterationModel);
		this.indicator = indicator;
	}

	private static GlobalIssueSetting getIssueSetting() {
		return OneDev.getInstance(SettingManager.class).getIssueSetting();
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		String message = null;
		if (getIteration().getStartDate() != null && getIteration().getDueDate() != null) {
			if (getIteration().getStartDate().before(getIteration().getDueDate())) {
				long startDay = toLocalDate(getIteration().getStartDate()).toEpochDay();
				long dueDay = toLocalDate(getIteration().getDueDate()).toEpochDay();
				if (dueDay - startDay >= MAX_DAYS) 
					message = "Iteration spans too long to show burndown chart";						
			} else {
				message = "Iteration start date should be before due date";
			}
		} else {
			message = "Iteration start and due date should be specified to show burndown chart";
		}
		if (message != null) {
			var fragment = new Fragment("content", "messageFrag", this);
			fragment.add(new Label("message", message));
			add(fragment);
		} else {
			var fragment = new Fragment("content", "chartFrag", this);
			fragment.add(new LineChartPanel("chart", new LoadableDetachableModel<>() {

				@Override
				protected LineSeries load() {
					long startDay = toLocalDate(getIteration().getStartDate()).toEpochDay();					
					long dueDay = toLocalDate(getIteration().getDueDate()).toEpochDay();

					Map<Long, Map<String, Integer>> dailyStateMetrics = new LinkedHashMap<>();
					for (IssueSchedule schedule : getIteration().getSchedules()) {
						Issue issue = schedule.getIssue();
						long scheduleDay = toLocalDate(schedule.getDate()).toEpochDay();

						Map<Long, Integer> dailyMetrics = new HashMap<>();
						var issueInfoManager = OneDev.getInstance(IssueInfoManager.class);
						var fromDay = Math.max(startDay, scheduleDay);
						var dailyStates = issueInfoManager.getDailyStates(issue, fromDay, dueDay);
						if (getIndicator().equals(REMAINING_TIME)) {
							var estimatedTime = issue.getOwnEstimatedTime();
							for (var entry: issueInfoManager.getDailySpentTimes(issue, fromDay, dueDay).entrySet()) 
								dailyMetrics.put(entry.getKey(), estimatedTime - entry.getValue());
						} else {
							int metric = getIndicatorValue(issue);
							for (var entry: dailyStates.entrySet())
								dailyMetrics.put(entry.getKey(), metric);
						}
						for (var entry : dailyStates.entrySet()) {
							var day = entry.getKey();
							var state = entry.getValue();
							Map<String, Integer> stateMetrics = dailyStateMetrics.computeIfAbsent(day, k -> new HashMap<>());
							if (state != null) {
								int metric = stateMetrics.getOrDefault(state, 0);
								metric += dailyMetrics.getOrDefault(day, 0);
								stateMetrics.put(state, metric);
							}
						}
					}

					List<String> xAxisValues = new ArrayList<>();
					for (Long day : dailyStateMetrics.keySet()) {
						var date = LocalDate.ofEpochDay(day);
						xAxisValues.add(String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()));
					}

					List<Line> lines = new ArrayList<>();

					int initialIssueMetric = 0;
					long today = LocalDate.now().toEpochDay();
					for (StateSpec spec : OneDev.getInstance(SettingManager.class).getIssueSetting().getStateSpecs()) {
						List<Integer> yAxisValues = new ArrayList<>();
						for (Map.Entry<Long, Map<String, Integer>> entry : dailyStateMetrics.entrySet()) {
							if (entry.getKey() <= today) {
								int metric = entry.getValue().getOrDefault(spec.getName(), 0);
								yAxisValues.add(metric);
							} else {
								yAxisValues.add(null);
							}
						}
						if (!yAxisValues.isEmpty()) {
							Integer stateMetric = yAxisValues.get(0);
							if (stateMetric == null)
								stateMetric = 0;
							initialIssueMetric += stateMetric;
						}
						lines.add(new Line(spec.getName(), yAxisValues, spec.getColor(), "States", null));
					}

					List<Integer> guidelineYAxisValues = new ArrayList<>();
					if (xAxisValues.size() > 1) {
						guidelineYAxisValues.add(initialIssueMetric);
						for (var i=2; i<xAxisValues.size(); i++)
							guidelineYAxisValues.add(null);
						guidelineYAxisValues.add(0);
					} else if (xAxisValues.size() == 1) {
						guidelineYAxisValues.add(initialIssueMetric);
					}

					lines.add(new Line("Guide Line", guidelineYAxisValues, "#7E8299", null, "dashed"));

					String yAxisValueFormatter;
					if (getIndicator().equals(REMAINING_TIME) 
							|| getIndicator().equals(ESTIMATED_TIME) 
							|| getIssueSetting().getFieldSpec(getIndicator()) instanceof WorkingPeriodField) {
						yAxisValueFormatter = String.format(""
								+ "function(value) {"
								+ "  if (value != undefined) "
								+ "    return onedev.server.util.formatWorkingPeriod(value, %b);"
								+ "  else "
								+ "    return '-';"
								+ "}", getIssueSetting().getTimeTrackingSetting().isUseHoursAndMinutesOnly());
					} else {
						yAxisValueFormatter = null;
					}
					return new LineSeries(null, xAxisValues, lines, yAxisValueFormatter, null, null);
				}

			}));

			var aggregationLink = OneDev.getInstance(SettingManager.class).getIssueSetting()
					.getTimeTrackingSetting().getAggregationLink();
			fragment.add(new Label("message", new AbstractReadOnlyModel<String>() {
				@Override
				public String getObject() {
					if (aggregationLink != null && (getIndicator().equals(ESTIMATED_TIME) || getIndicator().equals(REMAINING_TIME)))
						return"To avoid duplication, estimated/remaining time showing here does not include those aggregated from '" + aggregationLink + "'";
					else
						return null;
				}
			}) {
				@Override
				protected void onConfigure() {
					super.onConfigure();
					setVisible(getDefaultModelObject() != null);
				}
			});
			
			fragment.setOutputMarkupId(true);
			add(fragment);
		}
		
		setOutputMarkupId(true);
	}
	
	private String getIndicator() {
		if (indicator != null)
			return indicator;
		else 
			return getDefault(getIteration().getProject());
	}
	
	private int getIndicatorValue(Issue issue) {
		if (getIndicator().equals(ESTIMATED_TIME)) {
			return issue.getOwnEstimatedTime();
		} else if (getIndicator().equals(ISSUE_COUNT)) {
			return 1;
		} else {
			Object value = issue.getFieldValue(getIndicator());
			if (value != null) {
				if (value instanceof Integer)
					return (int) value;
				else
					return 0;
			} else {
				return 0;
			}
		}
	}
	
	private Iteration getIteration() {
		return getModelObject();
	}
	
}
