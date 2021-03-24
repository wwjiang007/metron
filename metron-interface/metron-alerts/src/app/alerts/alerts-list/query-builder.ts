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
import {Filter} from '../../model/filter';
import {ColumnNamesService} from '../../service/column-names.service';
import {SearchRequest} from '../../model/search-request';
import {SortField} from '../../model/sort-field';
import {TIMESTAMP_FIELD_NAME} from '../../utils/constants';
import {GroupRequest} from '../../model/group-request';
import {Group} from '../../model/group';
import { Injectable } from '@angular/core';

export enum FilteringMode {
  MANUAL = 'FilteringModeIsManual',
  BUILDER = 'FilteringModeIsBuilder',
}

@Injectable()
export class QueryBuilder {
  private _searchRequest = new SearchRequest();
  private _groupRequest = new GroupRequest();

  private _manualQuery;
  private _filters: Filter[] = [];

  private filteringMode: FilteringMode = FilteringMode.BUILDER;

  get query(): string {
    return this.searchRequest.query;
  }

  set query(value: string) {
    this.searchRequest.query = value;
  }

  get displayQuery(): string {
    return this.generateSelectForDisplay();
  }

  set filters(filters: Filter[]) {
    filters.forEach(filter =>  {
      this.addOrUpdateFilter(filter)
    });
  }

  get filters(): Filter[] {
    return this._filters;
  }

  get searchRequest(): SearchRequest {
    this._searchRequest.query = this.getQueryString() || '*';
    return this._searchRequest;
  }

  set searchRequest(value: SearchRequest) {
    this._searchRequest = value;
    this.setSearch(this._searchRequest.query);
  }

  groupRequest(scoreField): GroupRequest {
    this._groupRequest.query = this.getQueryString() || '*';
    this._groupRequest.scoreField = scoreField;
    return this._groupRequest;
  }

  setSearch(query: string) {
    this.updateFilters(query, true);
  }

  clearSearch() {
    this._filters = [];
    this._manualQuery = null;
  }

  addOrUpdateFilter(filter: Filter) {
    let existingFilterIndex = -1;

    if (filter.field === TIMESTAMP_FIELD_NAME) {
      const existingTimeRangeFilter = this.filters.find(fItem => fItem.field === TIMESTAMP_FIELD_NAME);
      if (existingTimeRangeFilter) {
        this.removeFilter(existingTimeRangeFilter);
      }
      this._filters.push(filter);
      return;
    }

    let existingFilter = this._filters.find((tFilter, index) => {
      if (filter.equals(tFilter)) {
        existingFilterIndex = index;
        return true;
      }
      return false;
    });

    if (existingFilter) {
      this._filters.splice(existingFilterIndex, 1, filter);
    } else {
      this._filters.push(filter);
    }
  }

  private getQueryString() {
    if (this.filteringMode === FilteringMode.MANUAL) {
      return this.getManualQuery();
    } else {
      return this.getBuilderQueryString();
    }
  }

  private getBuilderQueryString() {
    return this._filters.map(filter => filter.getQueryString()).join(' AND ');
  }

  generateNameForSearchRequest() {
    let select = this._filters.map(filter => ColumnNamesService.getColumnDisplayValue(filter.field) + ':' + filter.value).join(' AND ');
    return (select.length === 0) ? '*' : select;
  }

  generateSelectForDisplay() {
    return this._filters.reduce((appliedFilters, filter) => {
      if (filter.display) {
        appliedFilters.push(ColumnNamesService.getColumnDisplayValue(filter.field) + ':' + filter.value);
      }
      return appliedFilters;
    }, []).join(' AND ') || '*';
  }

  isTimeStampFieldPresent(): boolean {
    return this._filters.some(filter => (filter.field === TIMESTAMP_FIELD_NAME &&  !isNaN(Number(filter.value))));
  }

  removeFilter(filter: Filter) {
    this._filters = this._filters.filter(fItem => fItem !== filter );
  }

  removeFilterByField(field: string): void {
    this._filters = this._filters.filter(fItem => fItem.field !== field );
  }

  setFromAndSize(from: number, size: number) {
    this.searchRequest.from = from;
    this.searchRequest.size = size;
  }

  setGroupby(groups: string[]) {
    this._groupRequest.groups = groups.map(groupName => new Group(groupName));
  }

  setSort(sortBy: string, order: string) {
    let sortField = new SortField(sortBy, order);
    this.searchRequest.sort = [sortField];
  }

  setFilteringMode(mode: FilteringMode) {
    this.filteringMode = mode;
  }

  getFilteringMode() {
    return this.filteringMode;
  }

  setManualQuery(query: string) {
    this._manualQuery = query;
  }

  getManualQuery(): string {
    if (!this._manualQuery) {
      this._manualQuery = this.getBuilderQueryString() || '*';
    }
    return this._manualQuery;
  }

  private updateFilters(query: string, updateNameTransform = false) {
    this.removeDisplayedFilters();

    if (query && query !== '' && query !== '*') {
      let terms = query.split(' AND ');
      for (let term of terms) {
        let [field, value] = this.splitTerm(term);
        field = updateNameTransform ? ColumnNamesService.getColumnDisplayKey(field) : field;
        value = value.trim();

        this.addOrUpdateFilter(new Filter(field, value));
      }
    }
  }

  private splitTerm(term): string[] {
    const lastIdxOfSeparator = term.lastIndexOf(':');
    return [ term.substring(0, lastIdxOfSeparator), term.substring(lastIdxOfSeparator + 1) ];
  }

  private removeDisplayedFilters() {
    for (let i = this._filters.length - 1; i >= 0; i--) {
      if (this._filters[i].display) {
        this._filters.splice(i, 1);
      }
    }
  }
}
