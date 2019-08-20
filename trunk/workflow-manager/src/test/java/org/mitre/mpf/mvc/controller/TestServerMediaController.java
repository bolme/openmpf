/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.mvc.controller;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.mvc.model.DirectoryTreeNode;
import org.mitre.mpf.mvc.model.ServerMediaFilteredListing;
import org.mitre.mpf.mvc.model.ServerMediaListing;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDao;
import org.mitre.mpf.wfm.service.*;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class TestServerMediaController {

    private ServerMediaController _controller;

    @Mock
    private PropertiesUtil _mockProperties;

    @Mock
    private IoUtils _mockIoUtils;

    @Mock
    private HibernateJobRequestDao _mockJobRequestDao;

    @Mock
    private JsonUtils _mockJsonUtils;

    @Mock
    private HttpServletRequest _mockRequest;

    @Mock
    private S3StorageBackend _mockS3StorageBackend;

    private static final String _testFileName1 = "test-video.mp4";
    private static final String _testFileName2 = "test-document.pdf";
    private static final String _testFileName3 = "test-image.png";
    private ServerMediaService serverMediaService;
    private File mediaBase;

    @Rule
    public TemporaryFolder _rootTempFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder _extTempFolder = new TemporaryFolder();

    @Before
    public void init() throws InterruptedException {
        MockitoAnnotations.initMocks(this);

        mediaBase = _rootTempFolder.getRoot();

        when(_mockProperties.getServerMediaTreeRoot())
                .thenReturn(mediaBase.getAbsolutePath());
        when(_mockProperties.getRemoteMediaDirectory())
                .thenReturn(mediaBase);


        FileWatcherService fileCacheService = new FileWatcherServiceImpl(_mockProperties, _mockIoUtils);
        serverMediaService = new ServerMediaServiceImpl(fileCacheService);
        _controller = new ServerMediaController(_mockProperties, serverMediaService, _mockJobRequestDao,
                _mockJsonUtils, _mockS3StorageBackend);
        fileCacheService.launchWatcher(mediaBase.getAbsolutePath());
        // wait for cache to load
        Thread.sleep(100);
    }

    // get-all-directories

    @Test
    public void getAllDirectoriesJustRoot() {
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(rootNode.getFullPath(), _mockProperties.getServerMediaTreeRoot());
    }

    @Test
    public void getAllDirectoriesNested() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(100);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

    }

    @Test
    public void getAllDirectoriesAfterDeletion() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(100);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        Assert.assertTrue(subFolder.delete());

        Thread.sleep(100);
        rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        Assert.assertNull(rootNode.getNodes());
    }

    @Test
    public void getAllDirectoriesAfterEvents() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(100);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        File file = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file.createNewFile());

        Thread.sleep(100);
        DirectoryTreeNode newTree = _controller.getAllDirectories(_mockRequest).getBody();

        Assert.assertEquals(rootNode, newTree);

        Assert.assertTrue(file.delete());
        Thread.sleep(100);
        newTree = _controller.getAllDirectories(_mockRequest).getBody();

        Assert.assertEquals(rootNode, newTree);
    }

    // get-all-files

    @Test
    public void getAllFilesRootFolder() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(100);
        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();

        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName2, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(2).getName());
    }

    @Test
    public void getAllFilesNestedFolderAfterCreation() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(100);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertNotNull(rootNode.getNodes());
        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();
        Assert.assertEquals(0, mediaListing.getData().size());

        File file1 = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file1.createNewFile());
        File file2 = new File(subFolder.getAbsolutePath() + "/" + _testFileName2);
        Assert.assertTrue(file2.createNewFile());
        File file3 = new File(subFolder.getAbsolutePath() + "/" + _testFileName3);
        Assert.assertTrue(file3.createNewFile());

        Thread.sleep(100);
        mediaListing = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();

        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName2, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(2).getName());
    }

    @Test
    public void getAllFilesAfterDeletion() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(100);
        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();

        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName2, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(2).getName());

        Assert.assertTrue(file.delete());
        Thread.sleep(100);
        mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();

        assertEquals(2, mediaListing.getData().size());
        assertEquals(_testFileName2, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(1).getName());
    }

    @Test
    public void getFilesFromDeletedDirectory() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");

        Thread.sleep(100);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        File file = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file.createNewFile());

        Thread.sleep(100);
        ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());

        Assert.assertTrue(file.delete());
        Assert.assertTrue(subFolder.delete());

        Thread.sleep(100);
        rootNode = _controller.getAllDirectories(_mockRequest).getBody();
        Assert.assertNull(rootNode.getNodes());

        ServerMediaListing fileList = _controller.getAllFiles(_mockRequest, subFolder.getAbsolutePath()).getBody();
        Assert.assertNull(fileList);
    }

    @Test
    public void getFilesFromInvalidDirectory() {
        HttpStatus response = _controller.getAllFiles(_mockRequest, _mockProperties.getServerMediaTreeRoot() + "/lskjdflksjf").getStatusCode();
        Assert.assertEquals(HttpStatus.NOT_FOUND, response);

        response = _controller.getAllFiles(_mockRequest, "/lskjdflksjf").getStatusCode();
        Assert.assertEquals(HttpStatus.BAD_REQUEST, response);

        response = _controller.getAllFiles(_mockRequest, "/tmp").getStatusCode();
        Assert.assertEquals(HttpStatus.BAD_REQUEST, response);
    }

    // get-all-files-filtered

    @Test
    public void getFilesSorted() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(100);
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                mediaBase.getAbsolutePath(), 1 , 0, 10, "");

        ServerMediaListing mediaListing = mediaListingResult.getBody();
        assertEquals(3, mediaListing.getData().size());
        assertEquals(_testFileName2, mediaListing.getData().get(0).getName());
        assertEquals(_testFileName3, mediaListing.getData().get(1).getName());
        assertEquals(_testFileName1, mediaListing.getData().get(2).getName());
    }

    @Test
    public void getFilesFiltered() throws IOException, InterruptedException {
        File file = _rootTempFolder.newFile(_testFileName1);
        _rootTempFolder.newFile(_testFileName2);
        _rootTempFolder.newFile(_testFileName3);

        Assert.assertTrue(file.exists());
        Thread.sleep(800);
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                mediaBase.getAbsolutePath(), 1 , 0, 10, "mp4");

        ServerMediaListing mediaListing = mediaListingResult.getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());
    }

    @Test
    public void getFilesFilteredInvalidDirectory() {
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                "/tmp", 1 , 0, 10, "");
        assertSame(mediaListingResult.getStatusCode(), HttpStatus.BAD_REQUEST);

        mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                "/lskjdflksjf", 1 , 0, 10, "");
        assertSame(mediaListingResult.getStatusCode(), HttpStatus.BAD_REQUEST);

        mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                _mockProperties.getServerMediaTreeRoot() + "/slfkjasdlksdf", 1 , 0, 10, "");
        assertSame(mediaListingResult.getStatusCode(), HttpStatus.NOT_FOUND);
    }

    @Test
    public void getFilesFilteredDeletedDirectory() throws IOException, InterruptedException {
        File subFolder = _rootTempFolder.newFolder("nested-folder");
        Thread.sleep(100);
        DirectoryTreeNode rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertEquals(1, rootNode.getNodes().size());
        DirectoryTreeNode subNode = rootNode.getNodes().get(0);
        assertEquals(subNode.getFullPath(), subFolder.getAbsolutePath());

        File file = new File(subFolder.getAbsolutePath() + "/" + _testFileName1);
        Assert.assertTrue(file.createNewFile());
        Assert.assertTrue(file.exists());

        Thread.sleep(100);
        ResponseEntity<ServerMediaFilteredListing> mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                subFolder.getAbsolutePath(), 1 , 0, 10, "");
        ServerMediaListing mediaListing = mediaListingResult.getBody();
        assertEquals(1, mediaListing.getData().size());
        assertEquals(_testFileName1, mediaListing.getData().get(0).getName());

        Assert.assertTrue(file.delete());
        Assert.assertTrue(subFolder.delete());

        Thread.sleep(100);
        rootNode = _controller.getAllDirectories(_mockRequest).getBody();

        assertNull(rootNode.getNodes());

        mediaListingResult = _controller.getAllFilesFiltered(_mockRequest,
                subFolder.getAbsolutePath(), 1 , 0, 10, "");
        assertSame(mediaListingResult.getStatusCode(), HttpStatus.NOT_FOUND);
    }

    // @Test
    public void createAndRecreateSymlink() throws IOException, InterruptedException {
        createAndRemoveSymlink();

        // try to create the same symlink again to make sure everything was properly cleaned up the last time
        createAndRemoveSymlink();
    }

    @Test
    public void createAndRemoveSymlink() throws IOException, InterruptedException {
        for (int i = 0; i < 2; i++) {

            System.out.println("PASS: " + i); // DEBUG

            File extTestFile = _extTempFolder.newFile("test-text.txt");

            // create symlink

            Path target = _extTempFolder.getRoot().toPath();
            Path link = _rootTempFolder.getRoot().toPath().resolve("link");

            Files.createSymbolicLink(link, target);
            assertTrue(Files.isSymbolicLink(link));
            Thread.sleep(1000);

            ServerMediaListing mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
            assertEquals(0, mediaListing.getData().size());

            mediaListing = _controller.getAllFiles(_mockRequest, link.toFile().getAbsolutePath()).getBody();
            assertEquals(1, mediaListing.getData().size());
            assertEquals(extTestFile.getName(), mediaListing.getData().get(0).getName());

            // remove file in symlink dir

            System.out.println("DELETE 1"); // DEBUG
            assertTrue(extTestFile.delete());
            Thread.sleep(1000);
            System.out.println("DELETE 2"); // DEBUG

            mediaListing = _controller.getAllFiles(_mockRequest, link.toFile().getAbsolutePath()).getBody();

            System.out.println("CHECK 1"); // DEBUG
            Thread.sleep(1000);
            System.out.println("CHECK 2"); // DEBUG

            assertEquals(0, mediaListing.getData().size());

            // remove symlink

            assertTrue(link.toFile().delete());
            Thread.sleep(1000);

            mediaListing = _controller.getAllFiles(_mockRequest, mediaBase.getAbsolutePath()).getBody();
            assertEquals(0, mediaListing.getData().size());

            ResponseEntity<ServerMediaListing> mediaListingResult = _controller.getAllFiles(_mockRequest,
                    link.toFile().getAbsolutePath());
            assertSame(mediaListingResult.getStatusCode(), HttpStatus.NOT_FOUND);

            Thread.sleep(10000); // DEBUG
        }
    }
}