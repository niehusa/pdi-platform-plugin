/*!
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
* Foundation.
*
* You should have received a copy of the GNU Lesser General Public License along with this
* program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
* or from the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU Lesser General Public License for more details.
*
* Copyright (c) 2002-2017 Pentaho Corporation..  All rights reserved.
*/

package org.pentaho.platform.plugin.kettle;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

import org.pentaho.commons.connection.IPentahoResultSet;
import org.pentaho.commons.connection.memory.MemoryMetaData;
import org.pentaho.commons.connection.memory.MemoryResultSet;
import org.pentaho.di.repository.filerep.KettleFileRepositoryMeta;
import org.pentaho.platform.api.engine.ActionExecutionException;
import org.pentaho.platform.api.engine.IAuthorizationPolicy;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.ISolutionEngine;
import org.pentaho.platform.api.engine.IUserRoleListService;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.scheduler2.IScheduler;
import org.pentaho.platform.api.scheduler2.SchedulerException;
import org.pentaho.platform.engine.core.system.PathBasedSystemSettings;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.StandaloneSession;
import org.pentaho.platform.engine.core.system.boot.PlatformInitializationException;
import org.pentaho.platform.engine.security.SecurityHelper;
import org.pentaho.platform.engine.services.solution.SolutionEngine;
import org.pentaho.platform.plugin.kettle.security.policy.rolebased.actions.RepositoryExecuteAction;
import org.pentaho.platform.repository2.unified.fs.FileSystemBackedUnifiedRepository;
import org.pentaho.platform.scheduler2.quartz.QuartzScheduler;
import org.pentaho.test.platform.engine.core.MicroPlatform;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PdiActionTest {

  private QuartzScheduler scheduler;

  public static final String SESSION_PRINCIPAL = "SECURITY_PRINCIPAL";

  private String TEST_USER = "TestUser";

  private static final String SOLUTION_REPOSITORY = "test-src/solution";

  MicroPlatform mp = new MicroPlatform( SOLUTION_REPOSITORY );

  @Before
  public void init() throws SchedulerException, PlatformInitializationException {
    System.setProperty( "java.naming.factory.initial", "org.osjava.sj.SimpleContextFactory" ); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty( "org.osjava.sj.root", SOLUTION_REPOSITORY ); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty( "org.osjava.sj.delimiter", "/" ); //$NON-NLS-1$ //$NON-NLS-2$
    System.setProperty( "PENTAHO_SYS_CFG_PATH", new File( SOLUTION_REPOSITORY + "/pentaho.xml" ).getAbsolutePath() ); //$NON-NLS-2$

    IPentahoSession session = new StandaloneSession();
    PentahoSessionHolder.setSession( session );

    scheduler = new QuartzScheduler();
    scheduler.start();

    mp.define( IUserRoleListService.class, StubUserRoleListService.class );
    mp.define( UserDetailsService.class, StubUserDetailService.class );
    mp.defineInstance( IAuthorizationPolicy.class, new TestAuthorizationPolicy() );
    mp.setSettingsProvider( new PathBasedSystemSettings() );
    mp.defineInstance( IScheduler.class, scheduler );

    mp.define( ISolutionEngine.class, SolutionEngine.class );
    FileSystemBackedUnifiedRepository repo =  new FileSystemBackedUnifiedRepository( SOLUTION_REPOSITORY );
    mp.defineInstance(  IUnifiedRepository.class, repo );

    mp.start();

    SecurityHelper.getInstance().becomeUser( TEST_USER );
  }

  @After
  public void tearDown() {
    mp.stop();
  }

  @Test( expected = Exception.class )
  public void testValidatation() throws Exception {
    PdiAction action = new PdiAction();
    action.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    action.execute();
  }

  @Test
  public void testTransformationVariableOverrides() throws Exception {
    Map<String, String> variables = new HashMap<String, String>();
    variables.put( "customVariable", "customVariableValue" );

    String[] args = new String[] { "dummyArg" };

    Map<String, String> overrideParams = new HashMap<String, String>();
    overrideParams.put( "param2", "12" );

    PdiAction action = new PdiAction();
    action.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    action.setArguments( args );
    action.setVariables( variables );
    action.setParameters( overrideParams );
    action.setDirectory( SOLUTION_REPOSITORY );
    action.setTransformation( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testTransformationVariableOverrides.ktr" );
    action.execute();
    assertArrayEquals( args, action.localTrans.getArguments() );
    List<String> lines = FileUtils.readLines( new File( "testTransformationVariableOverrides.out.txt" ) );
    assertTrue( "File \"testTransformationVariableOverrides.out.txt\" should not be empty", lines.size() > 0 );
    String rowData = (String) lines.get( 1 );
    // Columns are as follows:
    // generatedRow|cmdLineArg1|param1|param2|repositoryDirectory|customVariable
    String[] columnData = rowData.split( "\\|" );
    assertEquals( "param1 value is wrong (default value should be in effect)", "param1DefaultValue", columnData[2]
        .trim() );
    assertEquals( "param2 value is wrong (overridden value should be in effect)", 12L, Long.parseLong( columnData[3]
        .trim() ) );

    assertEquals( "The number of rows generated should have equaled the value of param2", 13, lines.size() );

    assertEquals( "customVariable value is wrong", "customVariableValue", columnData[5].trim() );
  }

  @Test
  public void testLoadingFromVariousPaths() throws Exception {
    PdiAction action = new PdiAction();
    action.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    action.setArguments( new String[] { "dummyArg" } );
    action.setDirectory( SOLUTION_REPOSITORY  );
    action.setTransformation( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testLoadingFromVariousPaths.ktr" );
    action.execute();
  }

  @Test( expected = ActionExecutionException.class )
  public void testBadFileThrowsException() throws Exception {
    PdiAction action = new PdiAction();
    action.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    action.setArguments( new String[] { "dummyArg" } );
    action.setDirectory( "/" );
    action.setTransformation( "testBadFileThrowsException.ktr" );
    action.execute();
  }

  @Test
  public void testJobPaths() throws Exception {
    PdiAction component = new PdiAction();
    component.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    component.setDirectory( SOLUTION_REPOSITORY );
    component.setJob( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testJobPaths.kjb" );
    component.execute();
  }

  @Test
  public void testTransformationInjector() throws Exception {

    String[][] columnNames = { { "REGION", "DEPARTMENT", "POSITIONTITLE" } };
    MemoryMetaData metadata = new MemoryMetaData( columnNames, null );

    MemoryResultSet rowsIn = new MemoryResultSet( metadata );
    rowsIn.addRow( new Object[] { "abc", "123", "bogus" } );
    rowsIn.addRow( new Object[] { "region2", "Sales", "bad" } );
    rowsIn.addRow( new Object[] { "Central", "Sales", "test title" } );
    rowsIn.addRow( new Object[] { "Central", "xyz", "bad" } );

    PdiAction component = new PdiAction();
    component.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    component.setDirectory( SOLUTION_REPOSITORY );
    component.setTransformation( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testTransformationInjector.ktr" );
    component.setInjectorRows( rowsIn );
    component.setInjectorStep( "Injector" );
    component.setMonitorStep( "Output" );

    component.execute();

    assertEquals( 1, component.getTransformationOutputRowsCount() );
    assertEquals( 0, component.getTransformationOutputErrorRowsCount() );

    IPentahoResultSet rows = component.getTransformationOutputRows();
    assertNotNull( rows );
    assertEquals( 1, rows.getRowCount() );

    assertEquals( "Central", rows.getValueAt( 0, 0 ) );
    assertEquals( "Sales", rows.getValueAt( 0, 1 ) );
    assertEquals( "test title", rows.getValueAt( 0, 2 ) );
    assertEquals( "Hello, test title", rows.getValueAt( 0, 3 ) );

    rows = component.getTransformationOutputErrorRows();
    assertNotNull( rows );

    String log = component.getLog();
    assertTrue( log.indexOf( "Injector" ) != -1 );
    assertTrue( log.indexOf( "R=1" ) != -1 );
    assertTrue( log.indexOf( "Filter rows" ) != -1 );
    assertTrue( log.indexOf( "W=1" ) != -1 );
    assertTrue( log.indexOf( "Java Script Value" ) != -1 );
    assertTrue( log.indexOf( "W=1" ) != -1 );
    assertTrue( log.indexOf( "Output" ) != -1 );
    assertTrue( log.indexOf( "W=4" ) != -1 );
  }

  @Test
  public void testNoSettings() {
    PdiAction component = new PdiAction();
    component.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    try {
      component.validate();
      fail();
    } catch ( Exception ex ) {
      // ignored
    }
  }

  @Test
  public void testBadSolutionTransformation() {
    PdiAction component = new PdiAction();
    component.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    component.setTransformation( "testBadSolutionTransformation.ktr" );
    try {
      component.validate();
      fail();
    } catch ( Exception ex ) {
      // ignored
    }
  }

  @Test
  public void testBadSolutionJob() {
    PdiAction component = new PdiAction();
    component.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    component.setJob( "testBadSolutionJob.kjb" );
    try {
      component.validate();
      fail();
    } catch ( Exception ex ) {
      // ignored
    }
  }

  @Test
  public void testJobParameterPassing() throws Exception {
    String[] args = new String[] { "dummyArg" };

    Map<String, String> params = new HashMap<String, String>();
    params.put( "firstName", "John" );
    params.put( "lastName", "Doe" );

    // this job will pass these parameters to its underlying transformation
    // the transformation will simply add 'firstName' and 'lastName' into a 'fullName'
    PdiAction component = new PdiAction();
    component.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
    component.setDirectory( SOLUTION_REPOSITORY );
    component.setJob( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testJobParameterPassing.kjb" );
    component.setArguments( args );
    component.setParameters( params );
    component.execute();

    assertArrayEquals( args, component.localJob.getArguments() );
    // 1) check if job execution is successful
    assertEquals( 0, component.getResult() );
    assertEquals( "Finished", component.getStatus() );

    // 2) log scraping: check what the ktr's calculation was for ${first} + ${last} = ${fullName}
    String logScraping =
        component.getLog().substring( component.getLog().indexOf( "${first} + ${last} = ${fullName}" ), component
            .getLog().indexOf( "====================" ) );

    if ( logScraping != null && logScraping.contains( "fullName =" ) ) {
      logScraping = logScraping.substring( logScraping.indexOf( "fullName =" ) );
      logScraping = logScraping.substring( 0, logScraping.indexOf( "\n" ) );
      String fullName = logScraping.replace( "fullName =", "" ).trim();

      assertEquals( fullName.trim(), "JohnDoe" );
    }

    // 3) if no exception then the test passes
  }

  @Test
  public void testTransformationInitializationFail() throws Exception {
    try {
      PdiAction action = new PdiAction();
      action.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
      action.setDirectory( SOLUTION_REPOSITORY );
      action.setTransformation( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testTransformationInitializationFail.ktr" );

      action.execute();
    } catch ( Exception e ) {
      e.printStackTrace();
      fail( "Exception is thrown: " + e.getLocalizedMessage() );
    }
  }

  @Test
  public void testTransformationPrepareExecutionFailed() {
    try {
      PdiAction action = new PdiAction();
      action.setRepositoryName( KettleFileRepositoryMeta.REPOSITORY_TYPE_ID );
      action.setDirectory( SOLUTION_REPOSITORY );
      action.setTransformation( "/org/pentaho/platform/plugin/kettle/PdiActionTest_testTransformationPrepareExecutionFailed.ktr" );

      action.execute();
      assertTrue( action.isTransPrepareExecutionFailed() );
    } catch ( Exception e ) {
      e.printStackTrace();
      fail( "Exception is thrown: " + e.getLocalizedMessage() );
    }
  }

  public class TestAuthorizationPolicy implements IAuthorizationPolicy {

    List<String> allowedActions = new ArrayList<String>();

    @Override
    public List<String> getAllowedActions( String arg0 ) {
      allowedActions.add( "org.pentaho.repository.read" );
      allowedActions.add( "org.pentaho.repository.create" );
      return allowedActions;
    }

    @Override
    public boolean isAllowed( String arg0 ) {
      return true;
    }

  }

  public class TestAuthorizationPolicyNoExecute implements IAuthorizationPolicy {

    List<String> allowedActions = new ArrayList<String>();

    @Override
    public List<String> getAllowedActions( String arg0 ) {
      allowedActions.add( "org.pentaho.repository.read" );
      allowedActions.add( "org.pentaho.repository.create" );
      return allowedActions;
    }

    @Override
    public boolean isAllowed( String action ) {
      if ( action != null && action.equals( RepositoryExecuteAction.NAME ) ) {
        return false;
      }
      return true;
    }
  }
}
