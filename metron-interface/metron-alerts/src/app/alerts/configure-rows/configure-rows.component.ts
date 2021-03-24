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
import { Component, Input, HostListener, ElementRef, Output, EventEmitter } from '@angular/core';

export interface ConfigureRowsModel {
  values: {
    pageSize: number;
    refreshInterval: number;
  },
  triggerQuery: boolean
}
@Component({
  selector: 'app-configure-rows',
  templateUrl: './configure-rows.component.html',
  styleUrls: ['./configure-rows.component.scss']
})
export class ConfigureRowsComponent  {
  showView = false;

  @Input() srcElement: HTMLElement;
  @Input() pageSize: number;
  @Input() refreshInterval: number;

  @Output() configRowsChange = new EventEmitter<ConfigureRowsModel>();

  constructor(private elementRef: ElementRef) {}

  @HostListener('document:click', ['$event', '$event.target'])
  public onClick(event: MouseEvent, targetElement: HTMLElement): void {
    if (!targetElement) {
      return;
    }

    if (targetElement === this.srcElement || this.srcElement.contains(targetElement)) {
      this.showView = !this.showView;
      return;
    }

    const clickedInside = this.elementRef.nativeElement.contains(targetElement);
    if (!clickedInside) {
      this.showView = false;
    }
  }

  onPageSizeChange($event, parentElement) {
    parentElement.querySelector('.is-active').classList.remove('is-active');
    $event.target.classList.add('is-active');

    this.pageSize = parseInt($event.target.textContent.trim(), 10);
    this.propagateChanges(true);
  }

  onRefreshIntervalChange($event, parentElement) {
    parentElement.querySelector('.is-active').classList.remove('is-active');
    $event.target.classList.add('is-active');

    this.refreshInterval = parseInt($event.target.getAttribute('value').trim(), 10);
    this.propagateChanges();
  }

  onShowHideChange() {
    this.propagateChanges(true);
  }

  private propagateChanges(triggerQuery = false): void {
    this.configRowsChange.emit({
      values: {
        pageSize: this.pageSize,
        refreshInterval: this.refreshInterval,
      },
      triggerQuery,
    });
  }
}
