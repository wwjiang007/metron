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
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { Injectable } from '@angular/core';

import { ConfigureRowsComponent } from './configure-rows.component';
import { ConfigureTableService } from '../../service/configure-table.service';
import { ShowHideAlertEntriesComponent } from './show-hide/show-hide-alert-entries.component';
import { SwitchComponent } from 'app/shared/switch/switch.component';
import { TimezoneConfigComponent } from './timezone-config/timezone-config.component';

@Injectable()
class ConfigureTableServiceStub {}

describe('ConfigureRowsComponent', () => {
  let component: ConfigureRowsComponent;
  let fixture: ComponentFixture<ConfigureRowsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [
        ConfigureRowsComponent,
        ShowHideAlertEntriesComponent,
        SwitchComponent,
        TimezoneConfigComponent,
      ],
      providers: [
        { provide: ConfigureTableService, useValue: ConfigureTableServiceStub }
      ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigureRowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

});
