/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {forkJoin, noop, fromEvent} from 'rxjs';
import {Component, OnInit, ViewChild, ElementRef, OnDestroy, ChangeDetectorRef, AfterViewInit} from '@angular/core';
import {Router, NavigationStart} from '@angular/router';
import {Subscription} from 'rxjs';

import {Alert} from '../../model/alert';
import {SearchService} from '../../service/search.service';
import {UpdateService} from '../../service/update.service';
import {QueryBuilder, FilteringMode} from './query-builder';
import {ConfigureTableService} from '../../service/configure-table.service';
import {AlertsService} from '../../service/alerts.service';
import {ClusterMetaDataService} from '../../service/cluster-metadata.service';
import {ColumnMetadata} from '../../model/column-metadata';
import {SaveSearchService} from '../../service/save-search.service';
import {SaveSearch} from '../../model/save-search';
import {TableMetadata} from '../../model/table-metadata';
import {AlertSearchDirective} from '../../shared/directives/alert-search.directive';
import {SearchResponse} from '../../model/search-response';
import {ElasticsearchUtils} from '../../utils/elasticsearch-utils';
import {Filter} from '../../model/filter';
import { TIMESTAMP_FIELD_NAME, ALL_TIME } from '../../utils/constants';
import {TableViewComponent, PageChangedEvent, SortChangedEvent} from './table-view/table-view.component';
import {Pagination} from '../../model/pagination';
import {MetaAlertService} from '../../service/meta-alert.service';
import {Facets} from '../../model/facets';
import { GlobalConfigService } from '../../service/global-config.service';
import { DialogService } from 'app/service/dialog.service';
import { DialogType } from 'app/model/dialog-type';
import { Utils } from 'app/utils/utils';
import { AlertSource } from '../../model/alert-source';
import { AutoPollingService } from './auto-polling/auto-polling.service';
import { ConfigureRowsModel } from '../configure-rows/configure-rows.component';
import { SearchRequest } from 'app/model/search-request';
import { switchMap, map, debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-alerts-list',
  templateUrl: './alerts-list.component.html',
  styleUrls: ['./alerts-list.component.scss']
})
export class AlertsListComponent implements OnInit, OnDestroy {

  alertsColumns: ColumnMetadata[] = [];
  alertsColumnsToDisplay: ColumnMetadata[] = [];
  selectedAlerts: Alert[] = [];
  alerts: Alert[] = [];
  searchResponse: SearchResponse = new SearchResponse();
  colNumberTimerId: number;

  isMetaAlertPresentInSelectedAlerts = false;
  timeStampFilterPresent = false;

  readonly DEFAULT_TIME_RANGE = 'last-15-minutes';
  selectedTimeRange: Filter;

  @ViewChild('table') table: ElementRef;
  @ViewChild('dataViewComponent') dataViewComponent: TableViewComponent;
  @ViewChild(AlertSearchDirective) alertSearchDirective: AlertSearchDirective;

  private manualQueryFieldChangeSubs: Subscription;
  private manualQueryInputEl: ElementRef;
  @ViewChild('manualQuery') set manualQuery(el: ElementRef) {
    if (el) {
      this.manualQueryInputEl = el;
      this.manualQueryFieldChangeSubs = this.addManualQueryFieldChangeStream(el.nativeElement);
    }
  };
  get manualQuery(): ElementRef {
    return this.manualQueryInputEl;
  }

  tableMetaData = new TableMetadata();
  pagination: Pagination = new Pagination();
  alertChangedSubscription: Subscription;
  groupFacets: Facets;
  globalConfig: {} = {};
  configSubscription: Subscription;
  groups = [];
  subgroupTotal = 0;

  pendingSearch: Subscription;
  staleDataState = false;

  constructor(private router: Router,
              private searchService: SearchService,
              private updateService: UpdateService,
              private configureTableService: ConfigureTableService,
              private alertsService: AlertsService,
              private clusterMetaDataService: ClusterMetaDataService,
              private saveSearchService: SaveSearchService,
              private metaAlertsService: MetaAlertService,
              private globalConfigService: GlobalConfigService,
              private dialogService: DialogService,
              private cdRef: ChangeDetectorRef,
              public queryBuilder: QueryBuilder,
              public autoPollingSvc: AutoPollingService) {
    router.events.subscribe(event => {
      if (event instanceof NavigationStart && event.url === '/alerts-list') {
        this.selectedAlerts = [];
        this.restoreAutoPollingState();
      }
    });

    autoPollingSvc.data.subscribe((result: SearchResponse) => {
      this.setData(result);
      this.staleDataState = false;
    })
  }

  addAlertChangedListener() {
    this.metaAlertsService.alertChanged$.subscribe(alertSource => {
      if (alertSource['status'] === 'inactive') {
        this.removeAlert(alertSource)
      }
      this.updateAlert(alertSource);
    });

    this.alertChangedSubscription = this.updateService.alertChanged$.subscribe(alertSource => {
      this.updateAlert(alertSource);
    });
  }

  addAlertColChangedListener() {
    this.configureTableService.tableChanged$.subscribe(colChanged => {
      if (colChanged) {
        this.getAlertColumnNames(false);
      }
    });
  }

  addLoadSavedSearchListener() {
    this.saveSearchService.loadSavedSearch$.subscribe((savedSearch: SaveSearch) => {
      if (savedSearch.isManual === true) {
        this.queryBuilder.setFilteringMode(FilteringMode.MANUAL);
        this.queryBuilder.setManualQuery(savedSearch.searchRequest.query);
      } else {
        this.queryBuilder.setFilteringMode(FilteringMode.BUILDER);
      }

      this.queryBuilder.searchRequest = savedSearch.searchRequest;
      this.queryBuilder.filters = savedSearch.filters;
      this.setSelectedTimeRange(savedSearch.filters);
      this.prepareColumnData(savedSearch.tableColumns, []);
      this.timeStampFilterPresent = this.queryBuilder.isTimeStampFieldPresent();
      this.search(true, savedSearch);
    });
  }

  setSelectedTimeRange(filters: Filter[]) {
    filters.forEach(filter => {
      if (filter.field === TIMESTAMP_FIELD_NAME && filter.dateFilterValue) {
        this.selectedTimeRange = filter;
        this.updateQueryBuilder(filter);
      }
    });
  }

  calcColumnsToDisplay() {
    let availableWidth = document.documentElement.clientWidth - (200 + (15 + 15 + 25)); /* screenwidth - (navPaneWidth + (paddings))*/
    availableWidth = availableWidth - ((20 * 3) + 55 + 25); /* availableWidth - (score + colunSelectIcon +selectCheckbox )*/
    let tWidth = 0;
    this.alertsColumnsToDisplay =  this.alertsColumns.filter(colMetaData => {
      if (colMetaData.type.toUpperCase() === 'DATE') {
        tWidth += 140;
      } else if (colMetaData.type.toUpperCase() === 'IP') {
        tWidth += 120;
      } else if (colMetaData.type.toUpperCase() === 'BOOLEAN') {
        tWidth += 50;
      } else {
        tWidth += 130;
      }

      return tWidth < availableWidth;
    });
  }

  getAlertColumnNames(resetPaginationForSearch: boolean) {
    forkJoin(
        this.configureTableService.getTableMetadata(),
        this.clusterMetaDataService.getDefaultColumns()
    ).subscribe((response: any) => {
      this.prepareData(response[0], response[1]);
      this.setSearchRequestSize();
      this.refreshAlertData(resetPaginationForSearch);
    });
  }

  private refreshAlertData(resetPaginationForSearch: boolean) {
    if (this.alerts.length) {
      this.search(resetPaginationForSearch);
    }
  }

  ngOnDestroy() {
    this.autoPollingSvc.onDestroy();
    this.removeAlertChangedListner();
    this.configSubscription.unsubscribe();

    if (this.manualQueryFieldChangeSubs) {
      this.manualQueryFieldChangeSubs.unsubscribe();
    }
  }

  ngOnInit() {
    this.configSubscription = this.globalConfigService.get().subscribe((config: {}) => {
      this.globalConfig = config;
      if (this.globalConfig['source.type.field']) {
        let filteredAlertsColumns = this.alertsColumns.filter(colName => colName.name !== 'source:type');
        if (filteredAlertsColumns.length < this.alertsColumns.length) {
          this.alertsColumns = filteredAlertsColumns;
          this.alertsColumns.splice(2, 0, new ColumnMetadata(this.globalConfig['source.type.field'], 'string'));
        }
      }
    });

    this.setDefaultTimeRange(this.DEFAULT_TIME_RANGE);
    this.getAlertColumnNames(true);
    this.addAlertColChangedListener();
    this.addLoadSavedSearchListener();
    this.addAlertChangedListener();
  }

  private addManualQueryFieldChangeStream(inputDomEl: HTMLInputElement) {
    return fromEvent<KeyboardEvent>(inputDomEl, 'keyup').pipe(
      map(event => (event.target as HTMLInputElement).value),
      debounceTime(300),
    ).subscribe((manualQuery) => {
      this.onManualQueryInputChange(manualQuery);
    });
  }

  private onManualQueryInputChange(value: string) {
    this.queryBuilder.setManualQuery(value);
    this.staleDataState = true;
  }

  private setDefaultTimeRange(timeRangeId: string) {
    const timeRange = new Filter(TIMESTAMP_FIELD_NAME, timeRangeId, false);
    timeRange.dateFilterValue = Utils.timeRangeToDateObj(timeRange.value);
    this.setSelectedTimeRange([timeRange]);
  }

  onClear() {
    this.timeStampFilterPresent = false;
    this.queryBuilder.clearSearch();
    this.staleDataState = true;
  }

  onSearch(query: string) {
    this.queryBuilder.setSearch(query);
    this.timeStampFilterPresent = this.queryBuilder.isTimeStampFieldPresent();
    this.search();
    return false;
  }

  onAddFacetFilter($event) {
    this.onAddFilter(new Filter($event.name, $event.key));
  }

  onSortChanged(event: SortChangedEvent) {
    this.queryBuilder.setSort(event.sortBy, event.sortOrder);
    this.search(true);
  }

  onPageChanged(event: PageChangedEvent) {
    this.queryBuilder.setFromAndSize(event.from, event.size);
    this.search(false);
  }

  onSelectedAlertsChange(selectedAlerts) {
    this.selectedAlerts = selectedAlerts;
    this.isMetaAlertPresentInSelectedAlerts = this.selectedAlerts.some(
      alert => (alert.source.metron_alert && alert.source.metron_alert.length > 0)
    );

    this.autoPollingSvc.setSuppression(!!selectedAlerts.length);
  }

  onAddFilter(filter: Filter) {
    this.timeStampFilterPresent = this.queryBuilder.isTimeStampFieldPresent();
    this.queryBuilder.addOrUpdateFilter(filter);
    this.staleDataState = true;
  }

  onConfigRowsChange(config: ConfigureRowsModel) {
    const { values, triggerQuery } = config;

    this.tableMetaData.size = values.pageSize;
    this.updatePollingInterval(values.refreshInterval);
    this.saveSaveRowsConfig();

    if (triggerQuery) {
      this.search();
    }
  }

  private saveSaveRowsConfig() {
    this.configureTableService
      .saveTableMetaData(this.tableMetaData).subscribe(
        noop,
        () => console.log('Unable to save settings ....')
      );
  }

  onGroupsChange(groups) {
    this.groups = groups;
    this.queryBuilder.setGroupby(groups);
    this.search();
  }

  onResize() {
    clearTimeout(this.colNumberTimerId);
    this.colNumberTimerId = window.setTimeout(() => { this.calcColumnsToDisplay(); }, 500);
  }

  onTimeRangeChange(filter: Filter) {
    this.updateQueryBuilder(filter);
    this.staleDataState = true;
  }

  private updateQueryBuilder(timeRangeFilter: Filter) {
    if (timeRangeFilter.value === ALL_TIME) {
      this.queryBuilder.removeFilterByField(timeRangeFilter.field);
    } else {
      this.queryBuilder.addOrUpdateFilter(timeRangeFilter);
    }
  }

  prepareColumnData(configuredColumns: ColumnMetadata[], defaultColumns: ColumnMetadata[]) {
    this.alertsColumns = (configuredColumns && configuredColumns.length > 0) ? configuredColumns : defaultColumns;
    this.calcColumnsToDisplay();
  }

  prepareData(tableMetaData: TableMetadata, defaultColumns: ColumnMetadata[]) {
    this.prepareColumnData(tableMetaData.tableColumns, defaultColumns);
    this.tableMetaData = tableMetaData;
  }

  preventDropdownOptionIfDisabled(event: Event): boolean {
    if ((event.target as HTMLElement).classList.contains('disabled')) {
      event.stopPropagation();
      event.preventDefault();
      return false;
    }
    return true
  }

  processEscalate(event: Event) {
    if (this.preventDropdownOptionIfDisabled(event) === true) {
      this.updateService.updateAlertState(this.selectedAlerts, 'ESCALATE', false).subscribe(() => {
        const alerts = [...this.selectedAlerts];
        this.updateSelectedAlertStatus('ESCALATE');
        this.alertsService.escalate(alerts).subscribe();
      });
    }
  }

  processDismiss(event: Event) {
    if (this.preventDropdownOptionIfDisabled(event) === true) {
      this.updateService.updateAlertState(this.selectedAlerts, 'DISMISS', false).subscribe(results => {
        this.updateSelectedAlertStatus('DISMISS');
      });
    }
  }

  processOpen(event: Event) {
    if (this.preventDropdownOptionIfDisabled(event) === true) {
      this.updateService.updateAlertState(this.selectedAlerts, 'OPEN', false).subscribe(results => {
        this.updateSelectedAlertStatus('OPEN');
      });
    }
  }

  processResolve(event: Event) {
    if (this.preventDropdownOptionIfDisabled(event) === true) {
      this.updateService.updateAlertState(this.selectedAlerts, 'RESOLVE', false).subscribe(results => {
        this.updateSelectedAlertStatus('RESOLVE');
      });
    }
  }

  processAddToAlert(event: Event) {
    if (this.preventDropdownOptionIfDisabled(event) === true) {
      this.metaAlertsService.selectedAlerts = this.selectedAlerts;
      this.router.navigateByUrl('/alerts-list(dialog:add-to-meta-alert)');
    }
  }

  search(resetPaginationParams = true, savedSearch?: SaveSearch) {
    if (savedSearch) { this.saveCurrentSearch(savedSearch); }
    if (resetPaginationParams) {
      this.pagination.from = 0;
    }

    this.setSearchRequestSize();

    this.pendingSearch = this.searchService.search(this.queryBuilder.searchRequest).subscribe(
      results => {
        this.setData(results);
        this.pendingSearch = null;
        this.staleDataState = false;
      }, error => {
        this.setData(new SearchResponse());
        this.pendingSearch = null;
        this.dialogService.launchDialog('Server were unable to apply query string.', DialogType.Error);
      });

    if (this.autoPollingSvc.getIsPollingActive()) {
      this.autoPollingSvc.dropNextAndContinue();
    }
  }

  setSearchRequestSize() {
    if (this.groups.length === 0) {
      this.queryBuilder.searchRequest.from = this.pagination.from;
      if (this.tableMetaData.size) {
        this.pagination.size = this.tableMetaData.size;
      }
      this.queryBuilder.searchRequest.size = this.pagination.size;
    } else {
      this.queryBuilder.searchRequest.from = 0;
      this.queryBuilder.searchRequest.size = 0;
    }
  }

  saveCurrentSearch(savedSearch?: SaveSearch) {
    const isManual = () => this.queryBuilder.getFilteringMode() === FilteringMode.MANUAL;
    if (this.queryBuilder.query !== '*') {
      if (!savedSearch) {
        savedSearch = new SaveSearch();
        savedSearch.searchRequest = this.queryBuilder.searchRequest;
        savedSearch.tableColumns = this.alertsColumns;
        savedSearch.filters = this.queryBuilder.filters;
        savedSearch.searchRequest.query = isManual() ? this.queryBuilder.query : '';
        savedSearch.name = this.queryBuilder.generateNameForSearchRequest();
        savedSearch.isManual = isManual();
      }
      this.saveSearchService.saveAsRecentSearches(savedSearch).subscribe();
    }
  }

  setData(results: SearchResponse) {
    this.selectedAlerts = [];
    this.searchResponse = results;
    this.pagination.total = results.total;
    this.alerts = results.results ? results.results : [];
    this.setSelectedTimeRange(this.queryBuilder.filters);
    this.createGroupFacets(results);
  }

  private createGroupFacets(results: SearchResponse) {
    this.groupFacets = JSON.parse(JSON.stringify(results.facetCounts));
    if (this.groupFacets[this.globalConfig['source.type.field']]) {
      delete this.groupFacets[this.globalConfig['source.type.field']]['metaalert'];
    }
  }

  showConfigureTable() {
    this.autoPollingSvc.setSuppression(true);
    this.router.navigateByUrl('/alerts-list(dialog:configure-table)');
  }

  showDetails(alert: Alert) {
    this.selectedAlerts = [];
    this.selectedAlerts = [alert];
    this.autoPollingSvc.setSuppression(true);
    let sourceType = alert.source[this.globalConfig['source.type.field']];
    let url = '/alerts-list(dialog:details/' + sourceType + '/' + alert.source.guid + '/' + alert.index + ')';
    this.router.navigateByUrl(url);
  }

  showSavedSearches() {
    this.autoPollingSvc.setSuppression(true);
    this.router.navigateByUrl('/alerts-list(dialog:saved-searches)');
  }

  showSaveSearch() {
    this.autoPollingSvc.setSuppression(true);
    this.saveSearchService.setCurrentQueryBuilderAndTableColumns(this.queryBuilder, this.alertsColumns);
    this.router.navigateByUrl('/alerts-list(dialog:save-search)');
  }

  updateAlert(alertSource: AlertSource) {
    this.alerts.filter(alert => alert.source.guid === alertSource.guid)
            .map(alert => alert.source = alertSource);
  }

  removeAlert(alertSource: AlertSource) {
    this.alerts = this.alerts.filter(alert => alert.source.guid !== alertSource.guid);
  }

  updateSelectedAlertStatus(status: string) {
    for (let selectedAlert of this.selectedAlerts) {
      selectedAlert.source['alert_status'] = status;
    }
    this.selectedAlerts = [];
    this.autoPollingSvc.setSuppression(false);
  }

  removeAlertChangedListner() {
    this.alertChangedSubscription.unsubscribe();
  }

  onTreeViewChange(subgroupTotal) {
    this.subgroupTotal = subgroupTotal;
    this.cdRef.detectChanges();
  }

  getStaleDataWarning() {
    if (this.autoPollingSvc.getIsPollingActive()) {
      return `<i class="fa fa-warning" aria-hidden="true"></i> Data is in a stale state!
        Automatic refresh is turned on. Your filter and/or time-range changes will apply automatically on next refresh.`;
      } else {
      return `<i class="fa fa-warning" aria-hidden="true"></i> Data is in a stale state!
        Click <i class="fa fa-search" aria-hidden="true"></i> to update your view based
        on your current filter and time-range configuration!`;
    }
  }

  getPollingCongestionWarning() {
    return `<i class="fa fa-warning" aria-hidden="true"></i> Refresh interval is shorter than the response time.
      Please increase the refresh interval in the <i class="fa fa-sliders" aria-hidden="true"></i> menu above,
      or try to optimize your query filter.`;
  }

  private updatePollingInterval(refreshInterval: number): void {
    this.autoPollingSvc.setInterval(refreshInterval);
  }

  private restoreAutoPollingState() {
    if (this.autoPollingSvc.getIsPollingActive()) {
      this.autoPollingSvc.setSuppression(false);
    }
  }

  isQueryBuilderModeManual() {
    return this.queryBuilder.getFilteringMode() === FilteringMode.MANUAL;
  }

  toggleQueryBuilderMode() {
    // FIXME setting timerange on toggle feels like a hack
    this.setSelectedTimeRange([this.selectedTimeRange]);
    if (this.queryBuilder.getFilteringMode() === FilteringMode.BUILDER) {
      this.queryBuilder.setFilteringMode(FilteringMode.MANUAL);
    } else {
      this.queryBuilder.setFilteringMode(FilteringMode.BUILDER);
    }
  }

  queryForTreeView() {
    return this.queryBuilder.query;
  }

  onBuilderQueryChanged(query: string) {
    this.staleDataState = true;
  }

}
